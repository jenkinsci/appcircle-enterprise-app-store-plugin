package io.jenkins.plugins.ac;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

public class AppcircleBuilder extends Builder implements SimpleBuildStep {

    private final Secret accessToken;
    private final String entProfileId;
    private final String appPath;
    private final String summary;
    private final String releaseNote;
    private final String publishType;

    @DataBoundConstructor
    public AppcircleBuilder(
            Secret accessToken,
            String appPath,
            String entProfileId,
            String releaseNote,
            String summary,
            String publishType) {
        this.accessToken = accessToken;
        this.appPath = appPath;
        this.entProfileId = entProfileId;
        this.summary = summary;
        this.releaseNote = releaseNote;
        this.publishType = publishType;
    }

    void loginToAC(
            @NonNull Launcher launcher,
            @NonNull EnvVars env,
            @NonNull TaskListener listener,
            @NonNull FilePath workspace)
            throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("appcircle");
        args.add("login");
        args.add("--pat");
        args.add(getInputValue(this.accessToken.getPlainText(), "Access Token", env));

        int exitCode = launcher.launch()
                .cmds(args)
                .envs(env)
                .stdout(listener)
                .pwd(workspace)
                .join();

        if (exitCode != 0) {
            throw new IOException("Failed to log in to Appcircle. Exit code: " + exitCode);
        }
    }

    void uploadForProfile(
            @NonNull Launcher launcher,
            @NonNull EnvVars env,
            @NonNull TaskListener listener,
            @NonNull FilePath workspace)
            throws IOException, InterruptedException {
        // appcircle enterprise-app-store version upload-for-profile --entProfileId ${profileId} --app ${app}
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("appcircle");
        args.add("enterprise-app-store");
        args.add("version");
        args.add("upload-for-profile");
        args.add("--entProfileId");
        args.add(getInputValue(this.entProfileId, "Enterprise Profile ID", env));
        args.add("--app");
        args.add(getInputValue(this.appPath, "Build Path", env));

        int exitCode = launcher.launch()
                .cmds(args)
                .envs(env)
                .stdout(listener)
                .pwd(workspace)
                .join();

        if (exitCode != 0) {
            throw new IOException("Failed to upload app to Appcircle Enterprise store. Exit code: " + exitCode);
        }
    }

    String getStoreVersionList(@NonNull EnvVars env, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("appcircle");
        args.add("enterprise-app-store");
        args.add("version");
        args.add("list");
        args.add("--entProfileId");
        args.add(getInputValue(this.entProfileId, "Enterprise Profile ID", env));
        args.add("-o");
        args.add("json");

        try {
            // Execute the command
            ProcessBuilder processBuilder = new ProcessBuilder(args.toList());
            Process process = processBuilder.start();

            // Read the output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            listener.getLogger().println("Output of get: " + output.toString());

            // Parse the JSON output
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode list = objectMapper.readTree(output.toString());
            @Nullable String appVersionId = list.get(0).get("id").asText();
            if (appVersionId != null) {
                return appVersionId;
            }

            throw new IOException("Failed to fetch app version list");
        } catch (Exception e) {
            throw new IOException("Failed to fetch app version list " + e.getMessage());
        }
    }

    void uploadToStore(
            String appVersionId,
            @NonNull Launcher launcher,
            @NonNull EnvVars env,
            @NonNull TaskListener listener,
            @NonNull FilePath workspace)
            throws IOException, InterruptedException {
        // `appcircle enterprise-app-store version publish --entProfileId ${entProfileId} --entVersionId ${entVersionId}
        // --summary "${summary}" --releaseNotes "${releaseNote}" --publishType ${publishType}`;
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("appcircle");
        args.add("enterprise-app-store");
        args.add("version");
        args.add("publish");
        args.add("--entProfileId");
        args.add(getInputValue(this.entProfileId, "Enterprise Profile ID", env));
        args.add("--entVersionId");
        args.add(appVersionId);
        args.add("--summary");
        args.add(getInputValue(this.summary, "Summary", env));
        args.add("--releaseNotes");
        args.add(getInputValue(this.releaseNote, "Release Note", env));
        args.add("--publishType");
        args.add(getInputValue(this.publishType, "Publish Type", env));

        int exitCode = launcher.launch()
                .cmds(args)
                .envs(env)
                .stdout(listener)
                .pwd(workspace)
                .join();

        if (exitCode != 0) {
            throw new IOException("Failed to upload app to Appcircle Enterprise store. Exit code: " + exitCode);
        }
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
            //            listener.getLogger().println("Access Token Input: " + getInputValue(this.accessToken, "Access
            // Token", env));
            //            listener.getLogger().println("entProfileId Input: " + getInputValue(this.entProfileId,
            // "Profile ID", env));
            //            listener.getLogger().println("ReleaseNote: " + getInputValue(this.releaseNote, "Release Note",
            // env));
            //            listener.getLogger().println("appPath Input: " + this.appPath);
            //            listener.getLogger().println("message Input: " + this.summary);
            //            listener.getLogger().println("AC_PAT: " + env.get("AC_PAT"));
            //            listener.getLogger().println("PUBLISH TYPE: " + publishType);

            loginToAC(launcher, env, listener, workspace);
            uploadForProfile(launcher, env, listener, workspace);
            String appID = getStoreVersionList(env, listener);
            listener.getLogger().println("APP_ID: " + appID);
            uploadToStore(appID, launcher, env, listener, workspace);

        } catch (Exception e) {
            listener.getLogger().println("Failed to run command and parse JSON: " + e.getMessage());
            throw e;
        }
    }

    String getInputValue(@Nullable String inputValue, String inputFieldName, EnvVars envVars)
            throws IOException, InterruptedException {
        if (inputValue == null) {
            throw new IOException(inputFieldName + " is empty. Please fulfill the input");
        }

        Pattern pattern = Pattern.compile("\\$\\((.*?)\\)");
        Matcher appPathMatcher = pattern.matcher(inputValue);

        if (appPathMatcher.find()) {
            String variableName = inputValue.substring(2, inputValue.length() - 1);
            return envVars.get(variableName);
        }

        return inputValue;
    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @POST
        public FormValidation doCheckAccessToken(@QueryParameter String value) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (value.isEmpty()) return FormValidation.error("Access Token cannot be empty");
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckAppPath(@QueryParameter String value) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (value.isEmpty()) return FormValidation.error("App Path cannot be empty");
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckProfileId(@QueryParameter String value) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (value.isEmpty()) return FormValidation.error("Profile ID cannot be empty");
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckMessage(@QueryParameter String value) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

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
