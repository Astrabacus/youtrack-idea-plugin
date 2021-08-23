package com.github.jk1.ytplugin.scriptsDebug

import com.github.jk1.ytplugin.ComponentAware
import com.github.jk1.ytplugin.tasks.YouTrackServer
import com.github.jk1.ytplugin.timeTracker.TrackerNotification
import com.github.jk1.ytplugin.whenActive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.MalformedJsonException
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Url
import com.intellij.util.io.addChannelListener
import com.intellij.util.io.handler
import com.intellij.util.io.socketConnection.ConnectionStatus
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.NetUtil
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.debugger.Vm
import org.jetbrains.debugger.connection.RemoteVmConnection
import org.jetbrains.debugger.createDebugLogger
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.io.NettyUtil
import org.jetbrains.io.SimpleChannelInboundHandlerAdapter
import org.jetbrains.io.webSocket.WebSocketProtocolHandler
import org.jetbrains.io.webSocket.WebSocketProtocolHandshakeHandler
import org.jetbrains.wip.BrowserWipVm
import org.jetbrains.wip.WipVm
import org.jetbrains.wip.protocol.inspector.DetachedEventData
import java.awt.Desktop
import java.awt.Window
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64.getEncoder

open class WipConnection : RemoteVmConnection<WipVm>() {

    private var currentPageTitle: String? = null
    private val DEBUG_ADDRESS_ENDPOINT = "/api/debug/scripts/json"
    val url: Url? = null

    var pageUrl: String? = null
    var webSocketDebuggerUrl: String? = null
    var title: String? = null
    var type: String? = null
    var id: String? = null

    val logger: Logger get() = Logger.getInstance("com.github.jk1.ytplugin")

    private val String.b64Encoded: String
        get() = getEncoder().encodeToString(this.toByteArray(StandardCharsets.UTF_8))

    protected open fun createBootstrap() = BuiltInServerManager.getInstance().createClientBootstrap()

    override fun createBootstrap(address: InetSocketAddress, vmResult: AsyncPromise<WipVm>): Bootstrap {
        return createBootstrap().handler {
            val h = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build()

            it.pipeline().addLast(h.newHandler(NioSocketChannel().alloc(), address.hostName, address.port))
            it.pipeline()
                .addLast(HttpClientCodec(), HttpObjectAggregator(1048576 * 10), createChannelHandler(address, vmResult))
        }
    }

    protected open fun createChannelHandler(address: InetSocketAddress, vmResult: AsyncPromise<WipVm>): ChannelHandler {
        return object : SimpleChannelInboundHandlerAdapter<FullHttpResponse>() {
            override fun channelActive(context: ChannelHandlerContext) {
                super.channelActive(context)
                logger.debug("Channel is active")
                sendGetJson(address, context, vmResult)
            }

            override fun messageReceived(context: ChannelHandlerContext, message: FullHttpResponse) {
                try {
                    context.pipeline().remove(this)
                    connectToPage(context, address, message.content(), vmResult)
                } catch (e: Throwable) {
                    handleExceptionOnGettingWebSockets(e, vmResult)
                }
            }

            override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
                vmResult.setError(cause)
                context.close()
            }
        }
    }

    protected fun handleExceptionOnGettingWebSockets(e: Throwable, vmResult: AsyncPromise<WipVm>) {
        if (e is MalformedJsonException) {
            var message = "Invalid response from the remote host"
            val host = address?.hostName
            if (host != null && !NetUtil.isValidIpV4Address(host) && !NetUtil.isValidIpV6Address(host) &&
                host != "localhost" && host != "localhost6") {
                message += "Invalid connection to the hostname $host"
            }
            vmResult.setError(message)
        } else {
            vmResult.setError(e)
        }
    }

    private fun notifyUrlsShouldMatch() {
        val repo = getYouTrackRepo()
        when {
            !isBaseurlMatchingActual() && repo != null && webSocketDebuggerUrl != null -> {
                val note =
                    "Please verify that the server URL stored in settings for the YouTrack Integration plugin matches the base URL of your YouTrack site"
                val trackerNote = TrackerNotification()
                trackerNote.notifyWithHelper(note, NotificationType.WARNING, object : AnAction("Settings"), DumbAware {
                    override fun actionPerformed(event: AnActionEvent) {
                        event.whenActive {
                            val desktop: Desktop? = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
                            if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                                try {
                                    val repository = getYouTrackRepo()
                                    desktop.browse(URI("${repository?.url}/admin/settings"))
                                } catch (e: java.lang.Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                })
            }
            repo == null -> {
                val note = "The YouTrack Integration plugin has not been configured to connect with a YouTrack site"
                val trackerNote = TrackerNotification()
                trackerNote.notify(note, NotificationType.WARNING)
            }
            webSocketDebuggerUrl == null -> {
                val note =
                    "The debug operation requires that you have permission to update at least one project in YouTrack"
                val trackerNote = TrackerNotification()
                trackerNote.notify(note, NotificationType.WARNING)
            }
        }
    }

    private fun getActiveProject(): Project? {
        val projects = ProjectManager.getInstance().openProjects
        var activeProject: Project? = null
        for (project in projects) {

            var window: Window? = null
            // required to avoid threads exception
            try {
                ApplicationManager.getApplication().invokeAndWait {
                    window = WindowManager.getInstance().suggestParentWindow(project)
                }
            } catch (e: Exception) {
                logger.error("Unable to get the window: ${e.message}")
            }

            if (window != null && window!!.isEnabled) {
                activeProject = project
            }

        }
        return activeProject
    }

    protected fun sendGetJson(
        address: InetSocketAddress,
        context: ChannelHandlerContext,
        vmResult: AsyncPromise<WipVm>
    ) {

        val repository = getYouTrackRepo()
        val path = if (repository != null) URI(repository.url).path else ""

        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "$path$DEBUG_ADDRESS_ENDPOINT")
        request.headers().set(HttpHeaderNames.HOST, address.toHttpHeaderHostField())
        request.headers().set(HttpHeaderNames.ACCEPT, "*/*")
        request.headers()
            .set(HttpHeaderNames.AUTHORIZATION, "Basic ${"${repository?.username}:${repository?.password}".b64Encoded}")

        logger.debug("Request for the acquiring debug address is formed: ${request.uri()}")

        context.channel().writeAndFlush(request).addChannelListener {
            if (!it.isSuccess) {
                logger.debug("Request unsuccessful: ${it.cause()}")
                vmResult.setError(it.cause())
            }
        }
    }

    private fun getYouTrackRepo(): YouTrackServer? {
        val repositories =
            getActiveProject()?.let { ComponentAware.of(it).taskManagerComponent.getAllConfiguredYouTrackRepositories() }
        if (repositories != null && repositories.isNotEmpty()) {
            logger.debug("Obtained youtrack repo: ${repositories.first().url}")
            return repositories.first()
        } else {
            logger.debug("Failed to obtain youtrack repo")
        }
        return null
    }


    private fun isBaseurlMatchingActual(): Boolean {
        return webSocketDebuggerUrl != null && getYouTrackRepo() != null &&
                URI(webSocketDebuggerUrl).authority == URI(getYouTrackRepo()?.url).authority
    }

    override fun connectedAddressToPresentation(address: InetSocketAddress, vm: Vm): String {
        return "${super.connectedAddressToPresentation(address, vm)}${currentPageTitle?.let { " \u2013 $it" } ?: ""}"
    }

    protected open fun connectToPage(context: ChannelHandlerContext, address: InetSocketAddress,
                                     connectionsJson: ByteBuf, result: AsyncPromise<WipVm>): Boolean {

        result.onError {
            logger.debug("\"$it\"", "Error")
        }

        if (!connectionsJson.isReadable) {
            result.setError("Malformed response")
            logger.debug("Attempt to receive debug address: ${connectionsJson.readCharSequence(connectionsJson.readableBytes(), 
                Charset.forName("utf-8"))}")
            return true
        }

        val reader = JsonReader(ByteBufInputStream(connectionsJson).reader())
        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray()
        }

        while (reader.hasNext() && reader.peek() != JsonToken.END_DOCUMENT) {
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "url" -> pageUrl = reader.nextString()
                    "title" -> title = reader.nextString()
                    "type" -> type = reader.nextString()
                    "webSocketDebuggerUrl" -> webSocketDebuggerUrl = reader.nextString()
                    "id" -> id = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        logger.debug("YouTrack debug address obtained: $webSocketDebuggerUrl")

        notifyUrlsShouldMatch()
        return !processConnection(context, result)
    }

    protected open fun processConnection(
        context: ChannelHandlerContext,
        result: AsyncPromise<WipVm>
    ): Boolean {
        if ((url != null || webSocketDebuggerUrl != null) && isBaseurlMatchingActual()){
            logger.debug("Connect debugger for $url")
            connectDebugger(context, result)
            return true
        } else if (isBaseurlMatchingActual()){
            result.setError("Another debugger is attached, please ensure that configuration is stopped or restart application to force detach")
            logger.debug("Another debugger is attached, please ensure that configuration is stopped or restart application to force detach")
        }
        return true
    }

    protected open fun connectDebugger(
        context: ChannelHandlerContext,
        result: AsyncPromise<WipVm>
    ) {
        val handshaker = WebSocketClientHandshakerFactory.newHandshaker(
            URI.create(webSocketDebuggerUrl!!),
            WebSocketVersion.V13,
            null,
            false,
            null,
            100 * 1024 * 1024
        )
        val channel = context.channel()
        val vm = BrowserWipVm(debugEventListener, webSocketDebuggerUrl, channel, createDebugLogger("js.debugger.wip.log", ""))
        vm.title = title
        vm.commandProcessor.eventMap.add(DetachedEventData.TYPE) {
            if (it.reason() == "targetCrashed") {
                close("${ConnectionStatus.DISCONNECTED.statusText} (Inspector crashed)", ConnectionStatus.DISCONNECTED)
            } else {
                close("${ConnectionStatus.DISCONNECTED.statusText} (Inspector already opened)", ConnectionStatus.DETACHED)
            }
        }
        channel.pipeline().addLast(
            object : WebSocketProtocolHandshakeHandler(handshaker) {
                override fun completed() {
                    vm.initDomains()
                    result.setResult(vm)
                    vm.ready()
                }

                override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
                    result.setError(cause)
                    context.fireExceptionCaught(cause)
                }
            },
            WebSocketFrameAggregator(NettyUtil.MAX_CONTENT_LENGTH),
            object : WebSocketProtocolHandler() {
                override fun textFrameReceived(channel: Channel, message: TextWebSocketFrame) {
                    vm.textFrameReceived(message)
                }
            }
        )

        handshaker.handshake(channel).addChannelListener {
            if (!it.isSuccess) {
                context.fireExceptionCaught(it.cause())
            }
        }
    }

}

internal fun InetSocketAddress.toHttpHeaderHostField(): String =
    "${(this as? InetSocketAddress)?.hostName ?: hostString}:$port"