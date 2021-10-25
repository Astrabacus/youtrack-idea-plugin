package com.github.jk1.ytplugin.debug;

import com.github.jk1.ytplugin.ComponentAware;
import com.github.jk1.ytplugin.scriptsDebug.JSRemoteScriptsDebugConfiguration;
import com.github.jk1.ytplugin.tasks.YouTrackServer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class JSDebugScriptsEditor extends SettingsEditor<JSRemoteScriptsDebugConfiguration> {

  protected final Project project;
  private JPanel mainPanel;
  private JTextField folderField;
  Logger logger = Logger.getInstance("com.github.jk1.ytplugin");


  public JSDebugScriptsEditor(@NotNull Project project) {
    this.project = project;
  }

  protected void resetEditorFrom(@NotNull JSRemoteScriptsDebugConfiguration configuration) {
    String userFolder = configuration.getRootFolder();
    folderField.setText(userFolder);
  }

  @Override
  protected void applyEditorTo(JSRemoteScriptsDebugConfiguration configuration) {
    List<YouTrackServer> repositories = ComponentAware.Companion.of(project).getTaskManagerComponent().getAllConfiguredYouTrackRepositories();
    if (!repositories.isEmpty()) {
      logger.info("Apply Editor to non-empty YouTrack repository");
      try {
        configuration.setHost(new URL(repositories.get(0).getUrl()).getHost());
        configuration.setPort(new URL(repositories.get(0).getUrl()).getPort());
        logger.info("Apply Editor: " + configuration.getHost() + " " + configuration.getPort());
      } catch (MalformedURLException e) {
          logger.debug("Unable to apply editor");
          logger.debug(e);
      }
      configuration.setRootFolder(folderField.getText());
    }
  }



  @Override
  @NotNull
  protected JComponent createEditor() {
    return mainPanel;
  }

  {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
    $$$setupUI$$$();
  }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        folderField = new JTextField();
        folderField.setText("youtrack-scripts");
        panel1.add(folderField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Root folder for scripts:");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        label1.setLabelFor(folderField);
    }

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() {
    return mainPanel;
  }
}