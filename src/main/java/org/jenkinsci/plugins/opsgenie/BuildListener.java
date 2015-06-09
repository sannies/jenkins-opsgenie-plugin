package org.jenkinsci.plugins.opsgenie;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class BuildListener extends RunListener<AbstractBuild> {


    @Override
    public void onCompleted(AbstractBuild abstractBuild, TaskListener listener) {
        OpsGenieNotifier trigger = OpsGenieNotifier.getNotifier(abstractBuild.getProject());

        if (trigger == null) {
            return;
        }
        trigger.onCompleted(abstractBuild, listener);
    }

}
