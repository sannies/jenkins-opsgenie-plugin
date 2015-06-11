package org.jenkinsci.plugins.opsgenie;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class OpsGenieNotifier extends Notifier {

    private final static Logger LOG = Logger.getLogger(OpsGenieNotifier.class.getName());

    private final String apiKey;




    @DataBoundConstructor
    public OpsGenieNotifier(String apiKey) {
        super();
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    // ~~

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    // ~~

    @SuppressWarnings("unchecked")
    public static OpsGenieNotifier getNotifier(AbstractProject project) {
        Map<Descriptor<Publisher>, Publisher> map = project.getPublishersList().toMap();
        for (Publisher publisher : map.values()) {
            if (publisher instanceof OpsGenieNotifier) {
                return (OpsGenieNotifier) publisher;
            }
        }
        return null;
    }

    public void onCompleted(AbstractBuild build, TaskListener listener) {
        LOG.info("Prepare OpsGenie notification for build completed...");
        send(build, listener);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        //return super.perform(build, launcher, listener);
        return true;
    }

    // ~~ SNS specific implementation

    private void send(AbstractBuild build, TaskListener listener) {

        String apiKey = getDescriptor().getApiKey();
        if (isEmpty(apiKey)) {
            listener.error("No global API Key set");
            return;
        }


        try {
            LOG.info("Build done: " + build.getResult().toString());
            CloseableHttpClient httpclient = HttpClients.createDefault();
            if (build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
                HttpPost post = new HttpPost("https://api.opsgenie.com/v1/json/alert/close");
                String msg = "{\n" +
                        "     \"apiKey\": \"" + apiKey + "\",\n" +
                        "     \"alias\": \"" + build.getProject().getName() + "\"\n" +
                        "}'";
                //listener.getLogger().println(msg);
                post.setEntity(new StringEntity(msg, ContentType.APPLICATION_JSON));
                HttpResponse res = httpclient.execute(post);
                if (res.getStatusLine().getStatusCode() != 200) {
                    listener.error("OpsGenie Close API call failed: " + IOUtils.toString(res.getEntity().getContent()));
                } else {
                    listener.getLogger().println("OpsGenie 'Close Alert' API call failed: " + IOUtils.toString(res.getEntity().getContent()));
                }
            } else {
                HttpPost post = new HttpPost("https://api.opsgenie.com/v1/json/alert");

                String prjUrl = Jenkins.getInstance().getRootUrl() + Util.encode(build.getUrl());
                String description = "";
                List<String> logLines = build.getLog(500);
                for (String logLine : logLines) {
                    description += logLine;
                    description += "\n";
                }

                String msg = "{\n" +
                        "     \"apiKey\": \"" + apiKey + "\",\n" +
                        "     \"alias\": \"" + build.getProject().getName() + "\",\n" +
                        "     \"message\" : \"" + build.getProject().getFullDisplayName() + " is " + build.getResult().toString() + ". Go to " + prjUrl + " for details.\",\n" +
                        "     \"description\" : \"" + StringEscapeUtils.escapeJava(description) + "\"\n" +
                        "}";
                //listener.getLogger().println(msg);
                post.setEntity(new StringEntity(msg, ContentType.APPLICATION_JSON));
                HttpResponse res = httpclient.execute(post);
                if (res.getStatusLine().getStatusCode() != 200) {
                    listener.error("OpsGenie 'Create Alert' API call failed: " + IOUtils.toString(res.getEntity().getContent()));
                } else {
                    listener.getLogger().println("OpsGenie Alert created");
                }

            }


            listener.getLogger().println("notification: " + build.getResult().toString());
        } catch (Exception e) {
            listener.error("Failed to send OpsGenie Alert: " + e.getMessage());
        }
    }


    private boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }


    // ~~ Descriptor (part of Global Jenkins settings)

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String apiKey;

        public DescriptorImpl() {
            super(OpsGenieNotifier.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return "OpsGenie Alarm System";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            apiKey = formData.getString("apiKey");

            save();
            return super.configure(req, formData);
        }

        public String getApiKey() {
            return apiKey;
        }
    }

}
