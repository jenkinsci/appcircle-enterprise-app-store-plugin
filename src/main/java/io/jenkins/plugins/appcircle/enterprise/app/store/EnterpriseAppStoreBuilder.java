package io.jenkins.plugins.appcircle.enterprise.app.store;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import io.jenkins.plugins.appcircle.enterprise.app.store.Models.UserResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

public class EnterpriseAppStoreBuilder extends Builder implements SimpleBuildStep {

    private final Secret personalAPIToken;
    private final String appPath;
    private final String summary;
    private final String releaseNote;
    private final String publishType;

    @DataBoundConstructor
    public EnterpriseAppStoreBuilder(
            String personalAPIToken, String appPath, String releaseNote, String summary, String publishType) {
        this.personalAPIToken = Secret.fromString(personalAPIToken);
        this.appPath = appPath;
        this.summary = summary;
        this.releaseNote = releaseNote;
        this.publishType = publishType;
    }

    @Override
    public void perform(
            @NonNull Run<?, ?> run,
            @NonNull FilePath workspace,
            @NonNull EnvVars env,
            @NonNull Launcher launcher,
            @NonNull TaskListener listener)
            throws InterruptedException, IOException {
        try {
            UserResponse response = AuthService.getAcToken(this.personalAPIToken.getPlainText(), listener);
            listener.getLogger().println("Login is successful.");
            UploadService uploadService = new UploadService(response.getAccessToken(), listener);
            JSONObject uploadResponse = uploadService.uploadArtifact(this.appPath);
            Boolean result = uploadService.checkUploadStatus(uploadResponse.optString("taskId"), listener);

            if (result) {
                listener.getLogger().println("✔ App uploaded successfully.");
                if (!this.publishType.equals("0")) {
                    listener.getLogger().println("App is publishing.");
                    String profileId = uploadService.getProfileId();
                    String appVersionId = uploadService.getLatestAppVersionId(profileId);
                    Boolean isPublished = uploadService.publishEnterpriseAppVersion(
                            profileId, appVersionId, this.summary, this.releaseNote, this.publishType);
                    if (isPublished) {
                        listener.getLogger().println("App is published.");
                    } else {
                        listener.getLogger().println("Something went wrong. App could not published.");
                    }
                }
            }

        } catch (URISyntaxException e) {
            listener.error("Invalid URI: " + e.getMessage());
        } catch (Exception e) {
            listener.getLogger().println("Failed to run command and parse JSON: " + e.getMessage());
            throw e;
        }
    }

    @Symbol("appcircleEnterpriseAppStore")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @POST
        public FormValidation doCheckPersonalAPIToken(@QueryParameter String value) {
            if (value.isEmpty()) return FormValidation.error("Personal API Token cannot be empty");
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckAppPath(@QueryParameter String value) {
            if (value.isEmpty()) return FormValidation.error("App Path cannot be empty");
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckProfileId(@QueryParameter String value) {
            if (value.isEmpty()) return FormValidation.error("Profile ID cannot be empty");
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckMessage(@QueryParameter String value) {
            if (value.isEmpty()) return FormValidation.error("Message cannot be empty");
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.HelloWorldBuilder_DescriptorImpl_DisplayName();
        }
    }
}
