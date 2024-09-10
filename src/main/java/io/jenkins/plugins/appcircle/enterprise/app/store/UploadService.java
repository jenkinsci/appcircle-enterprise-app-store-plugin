package io.jenkins.plugins.appcircle.enterprise.app.store;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.appcircle.enterprise.app.store.Models.AppVersions;
import io.jenkins.plugins.appcircle.enterprise.app.store.Models.EnterpriseProfile;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

public class UploadService {
    private static final String BASE_URL = "https://api.appcircle.io";

    String authToken;

    @DataBoundConstructor
    public UploadService(String authToken) {
        this.authToken = authToken;
    }

    public JSONObject uploadArtifact(String appPath) throws IOException {
        String url = String.format("%s/store/v2/profiles/app-versions", BASE_URL);

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost uploadFile = new HttpPost(url);
        uploadFile.setHeader("Authorization", "Bearer " + this.authToken);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addPart("File", new FileBody(new File(appPath))).build();

        HttpEntity entity = builder.build();
        String contentType = entity.getContentType().getValue();

        String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);

        uploadFile.setHeader("Content-Type", "multipart/form-data; boundary=" + boundary);

        uploadFile.setEntity(entity);

        try (CloseableHttpResponse response = httpClient.execute(uploadFile)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());

            if (statusCode == 200) {
                try {
                    return new JSONObject(responseBody);
                } catch (JSONException e) {
                    throw new JSONException("Invalid JSON response: " + responseBody);
                }
            } else {
                throw new IOException("HTTP error " + statusCode + ": " + responseBody);
            }
        } catch (JSONException e) {
            throw new JSONException("Invalid JSON response: " + e);
        } catch (IOException e) {
            throw e;
        }
    }

    public Boolean publishEnterpriseAppVersion(
            String entProfileId, String entVersionId, String summary, String releaseNotes, String publishType)
            throws IOException {
        String url = String.format(
                "%s/store/v2/profiles/%s/app-versions/%s?action=publish", BASE_URL, entProfileId, entVersionId);
        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpPatch httpPatch = new HttpPatch(url);
        httpPatch.setHeader("Authorization", "Bearer " + this.authToken);
        httpPatch.setHeader("Content-Type", "application/json");

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("summary", summary);
        jsonBody.put("releaseNotes", releaseNotes);
        jsonBody.put("publishType", publishType);

        StringEntity requestEntity = new StringEntity(jsonBody.toString(), "UTF-8");
        httpPatch.setEntity(requestEntity);

        try (CloseableHttpResponse response = httpClient.execute(httpPatch)) {
            if (response.getStatusLine().getStatusCode() != 200) {
                return false;
            }

            return true;
        } catch (IOException e) {
            throw e;
        }
    }

    public AppVersions[] getAppVersions(String entProfileId) throws IOException {
        String url = String.format("%s/store/v2/profiles/%s/app-versions", BASE_URL, entProfileId);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet getRequest = new HttpGet(url);
        getRequest.setHeader("Authorization", "Bearer " + this.authToken);
        getRequest.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            JSONArray profilesArray = new JSONArray(responseBody);

            AppVersions[] appVersions = new AppVersions[profilesArray.length()];
            for (int i = 0; i < profilesArray.length(); i++) {
                JSONObject profileObject = profilesArray.getJSONObject(i);
                String id = profileObject.optString("id");
                String dateToCompare;

                if (profileObject.has("updateDate")
                        && !profileObject.optString("updateDate").isEmpty()) {
                    dateToCompare = profileObject.optString("updateDate");
                } else if (profileObject.has("createDate")
                        && !profileObject.optString("createDate").isEmpty()) {
                    dateToCompare = profileObject.optString("createDate");
                } else {
                    throw new IOException("Error: Build publication failed due to unparseable app versions.");
                }

                ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateToCompare, DateTimeFormatter.ISO_DATE_TIME);
                LocalDateTime updateDateTime = zonedDateTime.toLocalDateTime();
                appVersions[i] = new AppVersions(id, updateDateTime);
            }

            return appVersions;
        } catch (IOException e) {
            throw e;
        }
    }

    Boolean checkUploadStatus(String taskId) throws Exception {
        String url = String.format("%s/task/v1/tasks/%s", BASE_URL, taskId);
        String result = "";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", "Bearer " + this.authToken);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    result = EntityUtils.toString(entity);
                }

                JSONObject jsonResponse = new JSONObject(result);
                @Nullable Integer stateValue = jsonResponse.optInt("stateValue", -1);
                @Nullable String stateName = jsonResponse.optString("stateName");

                if (stateName == null) {
                    throw new Exception("Upload Status Could Not Received");
                } else if (stateValue == 2) {
                    throw new Exception(taskId + " id upload request failed with status " + stateName);
                } else if (stateValue == 1) {
                    Thread.sleep(2000);
                    return checkUploadStatus(taskId);
                } else if (stateValue == 3) {
                    return true;
                }
            }
        } catch (Exception e) {
            throw e;
        }

        return true;
    }

    public EnterpriseProfile[] getEntProfiles() throws IOException {
        String url = String.format("%s/store/v2/profiles", BASE_URL);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet getRequest = new HttpGet(url);
        getRequest.setHeader("Authorization", "Bearer " + this.authToken);
        getRequest.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
            String responseBody = EntityUtils.toString(response.getEntity());

            JSONArray profilesArray = new JSONArray(responseBody);

            EnterpriseProfile[] appVersions = new EnterpriseProfile[profilesArray.length()];
            for (int i = 0; i < profilesArray.length(); i++) {
                JSONObject profileObject = profilesArray.getJSONObject(i);
                String id = profileObject.optString("id");
                String name = profileObject.optString("name");
                String lastBinaryReceivedDate = profileObject.optString("lastBinaryReceivedDate");
                ZonedDateTime zonedDateTime =
                        ZonedDateTime.parse(lastBinaryReceivedDate, DateTimeFormatter.ISO_DATE_TIME);
                LocalDateTime lastBinaryReceivedDateTime = zonedDateTime.toLocalDateTime();

                appVersions[i] = new EnterpriseProfile(id, name, lastBinaryReceivedDateTime);
            }

            return appVersions;
        } catch (IOException e) {
            throw e;
        }
    }

    public String getProfileId() throws IOException {
        EnterpriseProfile[] profiles = getEntProfiles();

        Arrays.sort(profiles, new Comparator<EnterpriseProfile>() {
            @Override
            public int compare(EnterpriseProfile o1, EnterpriseProfile o2) {
                return o2.getLastBinaryReceivedDate().compareTo(o1.getLastBinaryReceivedDate());
            }
        });

        return profiles[0].getId();
    }

    public String getLatestAppVersionId(String profileId) throws IOException {
        AppVersions[] versions = getAppVersions(profileId);

        Arrays.sort(versions, new Comparator<AppVersions>() {
            @Override
            public int compare(AppVersions o1, AppVersions o2) {
                return o2.getUpdateDate().compareTo(o1.getUpdateDate());
            }
        });

        return versions[0].getId();
    }
}
