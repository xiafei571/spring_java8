import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;

public class ApiTestClient {

    // === 请在服务端自行填写这些参数 ===
    private static final String TOKEN_URL = "";       // 认证服务地址 (获取 access token)
    private static final String API_ENDPOINT = "";    // 目标 API 地址 (文件上传)
    private static final String SCOPE = "";           // e.g. api://xxxx/api-gateway/.default

    public static void main(String[] args) throws Exception {
        // 1. 获取 Access Token
        String tokenResponse = getAccessToken();
        System.out.println("Token Response: " + tokenResponse);

        // ⚠️ 你需要从 tokenResponse 里解析出 access_token 字段
        // 这里简单起见假设你直接取整个响应，实际用 JSON 库解析更安全
        String accessToken = extractAccessToken(tokenResponse);

        // 2. 上传文件
        uploadFile(accessToken, "sample.pdf");  // 你准备一个测试文件
    }

    private static String getAccessToken() throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(TOKEN_URL);
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");

            String body = "grant_type=client_credentials&scope=" + SCOPE;
            post.setEntity(new StringEntity(body));

            try (CloseableHttpResponse response = client.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String result = EntityUtils.toString(response.getEntity(), "UTF-8");

                if (statusCode != 200) {
                    throw new RuntimeException("Failed to get token, status=" + statusCode + ", body=" + result);
                }
                return result;
            }
        }
    }

    private static void uploadFile(String accessToken, String filePath) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(API_ENDPOINT);
            post.setHeader("Authorization", "Bearer " + accessToken);

            File file = new File(filePath);
            HttpEntity entity = MultipartEntityBuilder.create()
                    .addBinaryBody("content", file, ContentType.APPLICATION_PDF, file.getName())
                    .build();

            post.setEntity(entity);

            try (CloseableHttpResponse response = client.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String result = EntityUtils.toString(response.getEntity(), "UTF-8");

                System.out.println("Upload Response Status: " + statusCode);
                System.out.println("Response Body: " + result);
            }
        }
    }

    // 简单的解析函数（你可以替换成 Jackson 或 Gson）
    private static String extractAccessToken(String jsonResponse) {
        // 假设返回格式: {"access_token":"xxxxx","token_type":"Bearer","expires_in":3600}
        int start = jsonResponse.indexOf("access_token") + 15;
        int end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }
}
