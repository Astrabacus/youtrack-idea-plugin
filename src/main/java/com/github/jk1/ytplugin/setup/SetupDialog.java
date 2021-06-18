// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.github.jk1.ytplugin.setup;

import com.github.jk1.ytplugin.ComponentAware;
import com.github.jk1.ytplugin.HelpersKt;
import com.github.jk1.ytplugin.YouTrackPluginApiService;
import com.github.jk1.ytplugin.commands.ICommandService;
import com.github.jk1.ytplugin.issues.IssueStoreUpdaterService;
import com.github.jk1.ytplugin.issues.PersistentIssueStore;
import com.github.jk1.ytplugin.issues.model.TimeZone;
import com.github.jk1.ytplugin.navigator.SourceNavigatorService;
import com.github.jk1.ytplugin.tasks.TaskManagerProxyService;
import com.github.jk1.ytplugin.tasks.YouTrackServer;
import com.github.jk1.ytplugin.timeTracker.*;
import com.github.jk1.ytplugin.timeTracker.actions.StopTrackerAction;
import com.github.jk1.ytplugin.ui.HyperlinkLabel;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.AnyModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.tasks.youtrack.YouTrackRepository;
import com.intellij.tasks.youtrack.YouTrackRepositoryType;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.net.HttpConfigurable;
import kotlin.jvm.internal.Intrinsics;
import org.apache.commons.lang.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SetupDialog extends DialogWrapper implements ComponentAware {
    private JPanel myRootPane;
    private JTabbedPane mainPane;
    private JTextPane inputUrlTextPane;
    private JBPasswordField inputTokenField;
    private JBRadioButton isAutoTrackingEnabledRadioButton;
    private JBRadioButton isManualModeRadioButton;
    private JBRadioButton noTrackingButton;
    private JBCheckBox postWhenProjectClosedCheckbox;
    private JBCheckBox postWhenCommitCheckbox;
    private JBCheckBox isScheduledCheckbox;
    private JBLabel serverUrlLabel;
    private HyperlinkLabel advertiserLabel;
    private HyperlinkLabel getTokenInfoLabel;
    private JBCheckBox shareUrlCheckBox;
    private JBCheckBox useProxyCheckBox;
    private JBLabel tokenLabel;
    private JBLabel notifyFieldLabel;
    private JPanel autoPanel;
    private JPanel trackingModePanel;
    private JBLabel typeLabel;
    private PlaceholderTextField commentTextField;
    private JBLabel commentLabel;
    private JComboBox typeComboBox;
    private JPanel preferencesPanel;
    private JBTextField inactivityHourInputField;
    private JBTextField inactivityMinutesInputField;
    private JLabel hourLabel1;
    private JBLabel minuteLabel1;
    private JPanel inactivityPeriodPanel;
    private JBLabel inactivityTextField;
    private JBTextField scheduledHour;
    private JBTextField scheduledMinutes;
    private JBLabel minuteLabel2;
    private JBLabel hourLabel2;
    private JPanel timePanel;
    private JPanel timeTrackingTab;
    private JPanel connectionTab;
    private JBPanel<JBPanelWithEmptyText> controlPanel;

    Logger logger = Logger.getInstance("com.github.jk1.ytplugin");

    private boolean isConnectionTested;
    private final CredentialsChecker credentialsChecker;
    private final YouTrackRepository connectedRepository = new YouTrackRepository();
    private final SetupRepositoryConnector repoConnector = new SetupRepositoryConnector();
    private final boolean fromTracker;

    TestConnectionAction testConnectionAction = new TestConnectionAction();
    ProxyAction proxyAction = new ProxyAction();

    @NotNull
    private final Project project;

    @NotNull
    private YouTrackServer repo;

    @NotNull
    private final TimeTracker timer;


    public SetupDialog(@NotNull Project project, @NotNull YouTrackServer repo, Boolean fromTracker) {
        super(project, true);
        this.project = project;
        this.repo = repo;
        this.fromTracker = fromTracker;
        this.timer = getTimeTrackerComponent();
        this.credentialsChecker = getCredentialsCheckerComponent();
        setTitle("YouTrack");
        $$$setupUI$$$();
        init();
    }

    @Override
    protected void init() {
        if (timer.isRunning()) {
            new StopTrackerAction().stopTimer(project);
            timer.setAutoTrackingTemporaryDisabled(true);
        }

        setupGeneralTab();
        setupTimeTrackingTab();

        if (fromTracker) {
            proxyAction.setEnabled(false);
            testConnectionAction.setEnabled(false);
        }
        mainPane.setSelectedIndex(fromTracker ? 1 : 0);
        super.init();
    }

    private void setupGeneralTab() {

        if (!repo.getRepo().isConfigured()) {
            forbidSelection();
        } else {
            allowSelection(repo);
        }

        inputUrlTextPane.setBorder(BorderFactory.createLineBorder(JBColor.LIGHT_GRAY));
        inputUrlTextPane.setText(repo.getUrl());
        inputUrlTextPane.setBackground(inputTokenField.getBackground());
        // reset the default text area behavior to make TAB key transfer focus
        inputUrlTextPane.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        inputUrlTextPane.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        // make text area border behave similar to the one of the text field
        installDefaultBorder();

        inputUrlTextPane.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent arg0) {
                inputUrlTextPane.setBorder(BorderFactory.createCompoundBorder(
                        new DarculaTextBorder(),
                        BorderFactory.createEmptyBorder(0, 5, 0, 0)));
                repaint();
            }

            @Override
            public void focusLost(FocusEvent arg0) {
                installDefaultBorder();
                repaint();
            }
        });

        inputTokenField.setText(repo.getPassword());

        isScheduledCheckbox.setSelected(timer.isScheduledEnabled());
        scheduledFieldsEnabling(timer.isAutoTrackingEnable());

        controlPanel = new JBPanel<>();
        controlPanel.setLayout(null);

        mainPane.setMnemonicAt(0, KeyEvent.VK_1);
        mainPane.addChangeListener(e -> {
            if (mainPane.getSelectedIndex() == 1) {
                proxyAction.setEnabled(false);
                testConnectionAction.setEnabled(false);
            } else {
                proxyAction.setEnabled(true);
                testConnectionAction.setEnabled(true);
            }
        });
    }

    private void setupTimeTrackingTab() {

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(isAutoTrackingEnabledRadioButton);
        buttonGroup.add(isManualModeRadioButton);
        buttonGroup.add(noTrackingButton);

        noTrackingButton.setSelected(true);

        noTrackingButton.addActionListener(e ->
                isTrackingModeChanged(isAutoTrackingEnabledRadioButton.isSelected(),
                        isManualModeRadioButton.isSelected(), noTrackingButton.isSelected())
        );

        isAutoTrackingEnabledRadioButton.setSelected(timer.isAutoTrackingEnable());
        isAutoTrackingEnabledRadioButton.addActionListener(e ->
                isTrackingModeChanged(isAutoTrackingEnabledRadioButton.isSelected(),
                        isManualModeRadioButton.isSelected(), noTrackingButton.isSelected()));

        isManualModeRadioButton.setSelected(timer.isManualTrackingEnable());
        isManualModeRadioButton.addActionListener(e ->
                isTrackingModeChanged(isAutoTrackingEnabledRadioButton.isSelected(),
                        isManualModeRadioButton.isSelected(), noTrackingButton.isSelected())
        );

        postWhenCommitCheckbox.setSelected(timer.isPostAfterCommitEnabled());
        postWhenProjectClosedCheckbox.setSelected(timer.isWhenProjectClosedEnabled());

        postWhenProjectClosedCheckbox.setEnabled(timer.isAutoTrackingEnable());
        postWhenCommitCheckbox.setEnabled(timer.isAutoTrackingEnable());

        typeComboBox.setEditable(true);
        typeComboBox.setEnabled(timer.isAutoTrackingEnable() || timer.isManualTrackingEnable());
        typeLabel.setEnabled(timer.isAutoTrackingEnable() || timer.isManualTrackingEnable());

        Long inactivityHours = TimeUnit.MILLISECONDS.toHours(timer.getInactivityPeriodInMills());
        Long inactivityMinutes = TimeUnit.MILLISECONDS.toMinutes(timer.getInactivityPeriodInMills() -
                TimeUnit.HOURS.toMillis(inactivityHours));

        inactivityHourInputField.setText((inactivityHours < 10 ? "0" : "") + inactivityHours.toString());
        inactivityMinutesInputField.setText((inactivityMinutes < 10 ? "0" : "") + inactivityMinutes.toString());


        scheduledHour.setText(timer.getScheduledPeriod().substring(0, 2));
        scheduledMinutes.setText(timer.getScheduledPeriod().substring(3, 5));

        commentTextField.setText(timer.getComment());
        commentLabel.setEnabled(timer.isAutoTrackingEnable() || timer.isManualTrackingEnable());
        commentTextField.setEnabled(timer.isAutoTrackingEnable() || timer.isManualTrackingEnable());

    }

    @Override
    protected JButton createJButtonForAction(Action action) {
        JButton button = super.createJButtonForAction(action);
        button.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "apply");
        button.getActionMap().put("apply", action);
        return button;
    }

    void installDefaultBorder() {
        inputUrlTextPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new JTabbedPane().getBackground(), 2),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                        BorderFactory.createEmptyBorder(0, 5, 2, 2)
                )
        ));
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myRootPane;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{testConnectionAction, proxyAction, getOKAction(), getCancelAction()};
    }

    private void testConnectionAction() {

        Boolean isRememberPassword = PasswordSafe.getInstance().isRememberPasswordByDefault();
        if (!isRememberPassword) {
            repoConnector.setNoteState(NotifierState.PASSWORD_NOT_STORED);
        }
        Color fontColor = inputTokenField.getForeground();

        // current implementation allows to login with empty password (as guest) but we do not want to allow it
        if (!inputUrlTextPane.getText().isEmpty() && inputTokenField.getPassword().length != 0) {

            YouTrackRepositoryType myRepositoryType = new YouTrackRepositoryType();
            connectedRepository.setLoginAnonymously(false);

            if (inputUrlTextPane.getText().startsWith("http")) {
                connectedRepository.setUrl(inputUrlTextPane.getText());
            } else {
                connectedRepository.setUrl("http://" + inputUrlTextPane.getText());
            }
            connectedRepository.setPassword(new String(inputTokenField.getPassword()));
            connectedRepository.setUsername("random"); // ignored by YouTrack anyway when token is sent as password
            connectedRepository.setRepositoryType(myRepositoryType);
            connectedRepository.storeCredentials();

            connectedRepository.setShared(shareUrlCheckBox.isSelected());

            HttpConfigurable proxy = HttpConfigurable.getInstance();
            if (proxy.PROXY_HOST != null || !useProxyCheckBox.isSelected()) {
                connectedRepository.setUseProxy(useProxyCheckBox.isSelected());
                if (!inputUrlTextPane.getText().isEmpty() && inputTokenField.getPassword().length != 0) {
                    repoConnector.testConnection(connectedRepository, project);
                    connectedRepository.storeCredentials();
                }
            } else {
                repoConnector.setNoteState(NotifierState.NULL_PROXY_HOST);
                connectedRepository.setUseProxy(false);
            }
        }

        drawAutoCorrection(fontColor);

        if (inputUrlTextPane.getText().isEmpty() || inputTokenField.getPassword().length == 0) {
            repoConnector.setNoteState(NotifierState.EMPTY_FIELD);
        } else if (!(credentialsChecker.isMatchingAppPassword(connectedRepository.getPassword())
                || credentialsChecker.isMatchingBearerToken(connectedRepository.getPassword()))) {
            repoConnector.setNoteState(NotifierState.INVALID_TOKEN);
        } else if (PasswordSafe.getInstance().isMemoryOnly()) {
            repoConnector.setNoteState(NotifierState.PASSWORD_NOT_STORED);
        }

        if (repoConnector.getNoteState() != NotifierState.SUCCESS) {
            forbidSelection();
        } else {
            allowSelection(new YouTrackServer(connectedRepository, project));
        }

        repoConnector.setNotifier(notifyFieldLabel);
        isConnectionTested = true;
    }

    void forbidSelection() {
        noTrackingButton.setEnabled(false);
        isAutoTrackingEnabledRadioButton.setEnabled(false);
        isManualModeRadioButton.setEnabled(false);
        noTrackingButton.setSelected(true);
        isTrackingModeChanged(false, false, false);
    }


    public final void allowSelection(@NotNull YouTrackServer repository) {
        Intrinsics.checkNotNullParameter(repository, "repository");
        this.noTrackingButton.setEnabled(true);
        this.isAutoTrackingEnabledRadioButton.setEnabled(true);
        this.isManualModeRadioButton.setEnabled(true);

        try {
            final Collection<String> types = (new TimeTrackingService()).getAvailableWorkItemsTypes(repository);
            ApplicationManager.getApplication().invokeLater(() -> {
                int idx = 0;
                if (!types.isEmpty()) {
                    typeComboBox.setModel(new DefaultComboBoxModel(types.toArray()));
                    for (String type : types) {
                        if (type.equals(timer.getType())) {
                            idx = ArrayUtils.indexOf(types.toArray(), type);
                        }
                    }
                    typeComboBox.setSelectedIndex(idx);
                }
            }, AnyModalityState.ANY);
        } catch (Exception var3) {
            HelpersKt.getLogger().info("Work item types cannot be loaded: " + var3.getMessage());
            typeComboBox.setModel(new DefaultComboBoxModel(new String[]{timer.getType()}));
        }

    }


    private void isTrackingModeChanged(Boolean autoTrackEnabled, Boolean manualTrackEnabled, Boolean noTrackingEnabled) {

        scheduledHour.setEnabled(autoTrackEnabled);
        scheduledMinutes.setEnabled(autoTrackEnabled);

        inactivityFieldsEnabling(autoTrackEnabled && !noTrackingEnabled);
        scheduledFieldsEnabling(autoTrackEnabled && !noTrackingEnabled);

        postWhenProjectClosedCheckbox.setEnabled(autoTrackEnabled && !noTrackingEnabled);
        postWhenCommitCheckbox.setEnabled(autoTrackEnabled && !noTrackingEnabled);

        commentLabel.setEnabled((autoTrackEnabled || manualTrackEnabled) && !noTrackingEnabled);
        commentTextField.setEnabled((autoTrackEnabled || manualTrackEnabled) && !noTrackingEnabled);
        typeLabel.setEnabled((autoTrackEnabled || manualTrackEnabled) && !noTrackingEnabled);
        typeComboBox.setEnabled((autoTrackEnabled || manualTrackEnabled) && !noTrackingEnabled);

    }

    private void inactivityFieldsEnabling(Boolean enable) {
        inactivityHourInputField.setEnabled(enable);
        inactivityMinutesInputField.setEnabled(enable);
        inactivityTextField.setEnabled(enable);
        hourLabel1.setEnabled(enable);
        minuteLabel1.setEnabled(enable);
    }

    private void scheduledFieldsEnabling(Boolean enable) {
        isScheduledCheckbox.setEnabled(enable);
        scheduledHour.setEnabled(enable);
        scheduledMinutes.setEnabled(enable);
        hourLabel2.setEnabled(enable);
        minuteLabel2.setEnabled(enable);
    }

    private void drawAutoCorrection(Color fontColor) {

        if (repoConnector.getNoteState() == NotifierState.SUCCESS) {
            logger.info("YouTrack repository " + connectedRepository.getUrl() + " is connected");
            String oldAddress = inputUrlTextPane.getText();

            // if we managed to fix this and there's no protocol, well, it must be a default one missing

            URL oldUrl = null;
            URL fixedUrl = null;
            try {
                oldUrl = (oldAddress.startsWith("http")) ? new URL(oldAddress) : new URL("http://" + oldAddress);
                fixedUrl = new URL(connectedRepository.getUrl());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

            inputUrlTextPane.setText("");

            Color protocolColor = (oldUrl != null && oldUrl.getProtocol().equals(fixedUrl.getProtocol()) && oldAddress.startsWith("http"))
                    ? fontColor : JBColor.GREEN;

            appendToPane(inputUrlTextPane, fixedUrl.getProtocol(), protocolColor);
            appendToPane(inputUrlTextPane, "://", protocolColor);
            appendToPane(inputUrlTextPane, fixedUrl.getHost(), (oldUrl != null && oldUrl.getHost().equals(fixedUrl.getHost())) ? fontColor : JBColor.GREEN);
            if (fixedUrl.getPort() != -1) {
                Color color = (oldUrl.getPort() == fixedUrl.getPort() ? fontColor : JBColor.GREEN);
                appendToPane(inputUrlTextPane, ":", color);
                appendToPane(inputUrlTextPane, Integer.toString(fixedUrl.getPort()), color);
            }
            if (!fixedUrl.getPath().isEmpty()) {
                appendToPane(inputUrlTextPane, fixedUrl.getPath(), (oldUrl.getPath().equals(fixedUrl.getPath())) ? fontColor : JBColor.GREEN);
            }
        }
    }

    private void appendToPane(JTextPane tp, String msg, Color c) {
        StyleContext sc = StyleContext.getDefaultStyleContext();
        AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c);
        aset = sc.addAttribute(aset, StyleConstants.Alignment, StyleConstants.ALIGN_JUSTIFIED);
        tp.setCaretPosition(tp.getDocument().getLength());
        tp.setCharacterAttributes(aset, false);
        tp.replaceSelection(msg);
    }

    @Override
    public void doCancelAction() {
        new TimeTrackingService().setupTimeTracking(this, project);
        super.doCancelAction();
    }

    @Override
    protected void doOKAction() {
        if (!isConnectionTested) {
            testConnectionAction();
        }

        // current implementation allows to login with empty password (as guest) but we do not want to allow it
        if (repoConnector.getNoteState() != NotifierState.EMPTY_FIELD) {
            YouTrackRepository myRepository = repo.getRepo();
            myRepository.setLoginAnonymously(false);

            myRepository.setUrl(connectedRepository.getUrl());
            myRepository.setPassword(connectedRepository.getPassword());
            myRepository.setUsername(connectedRepository.getUsername());
            myRepository.setRepositoryType(connectedRepository.getRepositoryType());
            myRepository.storeCredentials();

            myRepository.setShared(connectedRepository.isShared());
            myRepository.setUseProxy(connectedRepository.isUseProxy());

            if (repoConnector.getNoteState() == NotifierState.SUCCESS) {
                repoConnector.showIssuesForConnectedRepo(myRepository, project);
            }

            new TimeTrackingService().setupTimeTracking(this, project);

        }

        if (repoConnector.getNoteState() != NotifierState.NULL_PROXY_HOST && repoConnector.getNoteState() !=
                NotifierState.PASSWORD_NOT_STORED && repoConnector.getNoteState() != NotifierState.EMPTY_FIELD) {
            this.close(0);
        }
        super.doOKAction();
    }


    @Override
    protected JComponent createCenterPanel() {
        return myRootPane;
    }

    @NotNull
    public Project getProject() {
        return this.project;
    }

    @NotNull
    public final YouTrackServer getRepo() {
        return this.repo;
    }

    @NotNull
    public TaskManagerProxyService getTaskManagerComponent() {
        return DefaultImpls.getTaskManagerComponent(this);
    }

    @NotNull
    public ICommandService getCommandComponent() {
        return DefaultImpls.getCommandComponent(this);
    }

    @NotNull
    public SourceNavigatorService getSourceNavigatorComponent() {
        return DefaultImpls.getSourceNavigatorComponent(this);
    }

    @NotNull
    public PersistentIssueWorkItemsStore getIssueWorkItemsStoreComponent() {
        return DefaultImpls.getIssueWorkItemsStoreComponent(this);
    }

    @NotNull
    public IssueWorkItemsStoreUpdaterService getIssueWorkItemsUpdaterComponent() {
        return DefaultImpls.getIssueWorkItemsUpdaterComponent(this);
    }

    @NotNull
    public PersistentIssueStore getIssueStoreComponent() {
        return DefaultImpls.getIssueStoreComponent(this);
    }

    @NotNull
    public IssueStoreUpdaterService getIssueUpdaterComponent() {
        return DefaultImpls.getIssueUpdaterComponent(this);
    }

    @NotNull
    public YouTrackPluginApiService getPluginApiComponent() {
        return DefaultImpls.getPluginApiComponent(this);
    }

    @NotNull
    public TimeTracker getTimeTrackerComponent() {
        return DefaultImpls.getTimeTrackerComponent(this);
    }

    @NotNull
    @Override
    public TimeZone getTimeZoneComponent() {
        return DefaultImpls.getTimeZoneComponent(this);
    }

    @NotNull
    public CredentialsChecker getCredentialsCheckerComponent() {
        return DefaultImpls.getCredentialsCheckerComponent(this);
    }

    public final JRadioButton getAutoTrackingEnabledCheckBox() {
        return isAutoTrackingEnabledRadioButton;
    }

    public final String getType() {
        return (String) this.typeComboBox.getItemAt(this.typeComboBox.getSelectedIndex());
    }

    public final String getInactivityHours() {
        return inactivityHourInputField.getText();
    }

    public final String getInactivityMinutes() {
        return inactivityMinutesInputField.getText();
    }

    public final JRadioButton getManualModeCheckbox() {
        return isManualModeRadioButton;
    }

    public final JCheckBox getScheduledCheckbox() {
        return isScheduledCheckbox;
    }

    public final JCheckBox getPostWhenCommitCheckbox() {
        return postWhenCommitCheckbox;
    }

    public final String getScheduledTime() {
        SimpleDateFormat formatter = new SimpleDateFormat("mm");
        try {
            String hours = formatter.format((new SimpleDateFormat("mm")).parse(this.scheduledHour.getText()));
            return hours + ':' + formatter.format((new SimpleDateFormat("mm")).parse(this.scheduledMinutes.getText())) + ":0";
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public final String getComment() {
        return commentTextField.getText();
    }

    public final JCheckBox getPostOnClose() {
        return postWhenProjectClosedCheckbox;
    }

    public final void setRepo(@NotNull YouTrackServer var1) {
        Intrinsics.checkNotNullParameter(var1, "<set-?>");
        this.repo = var1;
    }


    private void createUIComponents() {

        commentTextField = new PlaceholderTextField(timer.getComment());
        commentTextField.setPlaceholder("Enter default comment text");

        advertiserLabel = new HyperlinkLabel("Get YouTrack",
                "https://www.jetbrains.com/youtrack/download/get_youtrack.html?idea_integration", null);
        getTokenInfoLabel = new HyperlinkLabel("Learn how to generate a permanent token",
                "https://www.jetbrains.com/help/youtrack/incloud/Manage-Permanent-Token.html", null);
    }


    protected class TestConnectionAction extends DialogWrapperAction {
        protected TestConnectionAction() {
            super("Test Connection");
        }

        @Override
        protected void doAction(ActionEvent e) {
            testConnectionAction();
        }
    }

    protected class ProxyAction extends DialogWrapperAction {
        protected ProxyAction() {
            super("Proxy Settings...");
        }

        @Override
        protected void doAction(ActionEvent e) {
            HttpConfigurable.editConfigurable(controlPanel);
        }
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        myRootPane = new JPanel();
        myRootPane.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        myRootPane.setEnabled(true);
        myRootPane.setOpaque(true);
        mainPane = new JTabbedPane();
        myRootPane.add(mainPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(100, 200), null, 0, false));
        connectionTab = new JPanel();
        connectionTab.setLayout(new GridLayoutManager(12, 23, new Insets(20, 20, 30, 20), -1, -1));
        connectionTab.setInheritsPopupMenu(false);
        mainPane.addTab("General", connectionTab);
        inputUrlTextPane = new JTextPane();
        connectionTab.add(inputUrlTextPane, new GridConstraints(2, 1, 1, 22, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(500, -1), null, 0, false));
        serverUrlLabel = new JBLabel();
        serverUrlLabel.setText("Server URL:");
        connectionTab.add(serverUrlLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        connectionTab.add(spacer1, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        connectionTab.add(spacer2, new GridConstraints(4, 12, 1, 11, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        getTokenInfoLabel.setText("Learn how to generate a permanent token");
        connectionTab.add(getTokenInfoLabel, new GridConstraints(6, 11, 1, 12, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        shareUrlCheckBox = new JBCheckBox();
        shareUrlCheckBox.setText("Share URL");
        connectionTab.add(shareUrlCheckBox, new GridConstraints(3, 22, 1, 1, GridConstraints.ANCHOR_NORTHEAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        advertiserLabel.setText("Get YouTrack");
        connectionTab.add(advertiserLabel, new GridConstraints(1, 12, 1, 11, GridConstraints.ANCHOR_SOUTHEAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        connectionTab.add(spacer3, new GridConstraints(7, 15, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(-1, 40), new Dimension(-1, 40), null, 0, false));
        final Spacer spacer4 = new Spacer();
        connectionTab.add(spacer4, new GridConstraints(8, 15, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final Spacer spacer5 = new Spacer();
        connectionTab.add(spacer5, new GridConstraints(9, 15, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final Spacer spacer6 = new Spacer();
        connectionTab.add(spacer6, new GridConstraints(10, 15, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final Spacer spacer7 = new Spacer();
        connectionTab.add(spacer7, new GridConstraints(11, 15, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        tokenLabel = new JBLabel();
        tokenLabel.setText("Permanent token:");
        connectionTab.add(tokenLabel, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        inputTokenField = new JBPasswordField();
        inputTokenField.setText("");
        connectionTab.add(inputTokenField, new GridConstraints(5, 1, 1, 22, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        notifyFieldLabel = new JBLabel();
        notifyFieldLabel.setText("");
        connectionTab.add(notifyFieldLabel, new GridConstraints(7, 0, 1, 10, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        useProxyCheckBox = new JBCheckBox();
        useProxyCheckBox.setText("Use proxy");
        connectionTab.add(useProxyCheckBox, new GridConstraints(3, 21, 1, 1, GridConstraints.ANCHOR_NORTHEAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("");
        connectionTab.add(label1, new GridConstraints(3, 17, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer8 = new Spacer();
        connectionTab.add(spacer8, new GridConstraints(3, 19, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final Spacer spacer9 = new Spacer();
        connectionTab.add(spacer9, new GridConstraints(3, 20, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        timeTrackingTab = new JPanel();
        timeTrackingTab.setLayout(new GridLayoutManager(4, 1, new Insets(20, 20, 20, 20), -1, -1));
        mainPane.addTab("Time Tracking", timeTrackingTab);
        autoPanel = new JPanel();
        autoPanel.setLayout(new GridLayoutManager(4, 23, new Insets(10, 10, 10, 10), 20, 20));
        timeTrackingTab.add(autoPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        autoPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Automatically create work items", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        postWhenProjectClosedCheckbox = new JBCheckBox();
        postWhenProjectClosedCheckbox.setText("When closing the project");
        autoPanel.add(postWhenProjectClosedCheckbox, new GridConstraints(0, 0, 1, 22, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        postWhenCommitCheckbox = new JBCheckBox();
        postWhenCommitCheckbox.setText("When commiting changes");
        autoPanel.add(postWhenCommitCheckbox, new GridConstraints(0, 22, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        isScheduledCheckbox = new JBCheckBox();
        isScheduledCheckbox.setText("On a set schedule at:");
        autoPanel.add(isScheduledCheckbox, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer10 = new Spacer();
        autoPanel.add(spacer10, new GridConstraints(3, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        timePanel = new JPanel();
        timePanel.setLayout(new GridLayoutManager(1, 5, new Insets(0, 0, 0, 0), -1, -1));
        autoPanel.add(timePanel, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        scheduledHour = new JBTextField();
        scheduledHour.setText("19");
        timePanel.add(scheduledHour, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(30, -1), null, 0, false));
        scheduledMinutes = new JBTextField();
        scheduledMinutes.setText("00");
        timePanel.add(scheduledMinutes, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(30, -1), null, 0, false));
        final Spacer spacer11 = new Spacer();
        timePanel.add(spacer11, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        hourLabel2 = new JBLabel();
        hourLabel2.setText("hours");
        timePanel.add(hourLabel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        minuteLabel2 = new JBLabel();
        minuteLabel2.setText("minutes");
        timePanel.add(minuteLabel2, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        inactivityPeriodPanel = new JPanel();
        inactivityPeriodPanel.setLayout(new GridLayoutManager(1, 5, new Insets(0, 0, 0, 0), -1, -1));
        autoPanel.add(inactivityPeriodPanel, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        inactivityHourInputField = new JBTextField();
        inactivityHourInputField.setText("00");
        inactivityPeriodPanel.add(inactivityHourInputField, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(30, -1), null, 0, false));
        inactivityMinutesInputField = new JBTextField();
        inactivityMinutesInputField.setText("15");
        inactivityPeriodPanel.add(inactivityMinutesInputField, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(30, -1), null, 0, false));
        final Spacer spacer12 = new Spacer();
        inactivityPeriodPanel.add(spacer12, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        hourLabel1 = new JLabel();
        hourLabel1.setText("hours");
        inactivityPeriodPanel.add(hourLabel1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        minuteLabel1 = new JBLabel();
        minuteLabel1.setText("minutes");
        inactivityPeriodPanel.add(minuteLabel1, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        inactivityTextField = new JBLabel();
        inactivityTextField.setText("Inactivity period:");
        autoPanel.add(inactivityTextField, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        preferencesPanel = new JPanel();
        preferencesPanel.setLayout(new GridLayoutManager(3, 7, new Insets(10, 10, 10, 10), -1, -1));
        timeTrackingTab.add(preferencesPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        preferencesPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Preferences", TitledBorder.LEFT, TitledBorder.DEFAULT_POSITION, null, null));
        typeLabel = new JBLabel();
        typeLabel.setText("Work type:");
        preferencesPanel.add(typeLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        commentLabel = new JBLabel();
        commentLabel.setText("Comment:");
        preferencesPanel.add(commentLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        typeComboBox = new JComboBox();
        preferencesPanel.add(typeComboBox, new GridConstraints(0, 1, 1, 6, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        commentTextField.setPlaceholder("Enter default comment text");
        commentTextField.setText("  a");
        commentTextField.setToolTipText("");
        preferencesPanel.add(commentTextField, new GridConstraints(2, 1, 1, 6, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final Spacer spacer13 = new Spacer();
        preferencesPanel.add(spacer13, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(-1, 10), null, 0, false));
        trackingModePanel = new JPanel();
        trackingModePanel.setLayout(new GridLayoutManager(1, 3, new Insets(10, 10, 10, 10), 20, 20));
        timeTrackingTab.add(trackingModePanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        trackingModePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Tracking mode", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        isAutoTrackingEnabledRadioButton = new JBRadioButton();
        isAutoTrackingEnabledRadioButton.setText("Automatic");
        trackingModePanel.add(isAutoTrackingEnabledRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        isManualModeRadioButton = new JBRadioButton();
        isManualModeRadioButton.setText("Manual");
        trackingModePanel.add(isManualModeRadioButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        noTrackingButton = new JBRadioButton();
        noTrackingButton.setText("Off");
        trackingModePanel.add(noTrackingButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 1, new Insets(10, 10, 10, 3), -1, -1));
        timeTrackingTab.add(panel1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer14 = new Spacer();
        panel1.add(spacer14, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myRootPane;
    }

}


