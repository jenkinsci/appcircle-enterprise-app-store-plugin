package io.jenkins.plugins.appcircle.enterprise.app.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class EnterpriseAppStoreBuilderSubOrganizationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static EnterpriseAppStoreBuilder builder() {
        return new EnterpriseAppStoreBuilder("my-pat", "app.ipa", "Release notes", "Summary", "0");
    }

    @Test
    public void subOrganizationNameSurvivesConfigRoundtrip() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        EnterpriseAppStoreBuilder builder = builder();
        builder.setSubOrganizationName("PlugInTest");
        project.getBuildersList().add(builder);

        project = j.configRoundtrip(project);

        EnterpriseAppStoreBuilder after = project.getBuildersList().get(EnterpriseAppStoreBuilder.class);
        assertEquals("PlugInTest", after.getSubOrganizationName());
    }

    @Test
    public void subOrganizationNameIsOptional() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(builder());

        project = j.configRoundtrip(project);

        EnterpriseAppStoreBuilder after = project.getBuildersList().get(EnterpriseAppStoreBuilder.class);
        String name = after.getSubOrganizationName();
        assertTrue(name == null || name.isEmpty());
    }
}
