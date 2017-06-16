package chatbot.lib.api;

import chatbot.lib.Utility;
import chatbot.lib.handlers.NLHandler;
import chatbot.lib.response.ResponseData;
import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by ramgathreya on 6/2/17.
 */
public class QAService {
    private static final int timeout = 0;
    private static final String URL = "https://wdaqua-qanary.univ-st-etienne.fr/gerbil-execute/wdaqua-core0,%20QueryExecuter/";

    private HttpClient client;

    public QAService() {
        RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(this.timeout).build();
        this.client = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
    }

    private String makeRequest(String question) throws Exception {
        HttpPost httpPost = new HttpPost(URL);
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("query", question));

        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, Consts.UTF_8);
        httpPost.setEntity(entity);

        HttpResponse response = client.execute(httpPost);

        // Error Scenario
        if(response.getStatusLine().getStatusCode() >= 400) {
            throw new Exception("QANARY Server could not answer due to: " + response.getStatusLine());
        }

        return EntityUtils.toString(response.getEntity());
    }

    // Calls QA Service then returns resulting data as a list of Data Objects. The Data class is defined below as an inner class to be used here locally
    public List<QAService.Data> search(String question) throws Exception {
        String response = makeRequest(question);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(response);
        JsonNode answers = mapper.readTree(rootNode.findValue("questions").get(0).get("question").get("answers").getTextValue());

        if (answers != null) {
            JsonNode bindings = answers.get("results").get("bindings");
            List<QAService.Data> data = new ArrayList<>();

            for(JsonNode binding : bindings) {
                Iterator<Map.Entry<String, JsonNode>> nodes = binding.getFields();
                while (nodes.hasNext()) {
                    Map.Entry<String, JsonNode> entry = nodes.next();
                    JsonNode value = entry.getValue();
                    data.add(new QAService.Data(value.get("type").getTextValue(), value.get("value").getTextValue()));
                }
            }
            if(data.size() > ResponseData.MAX_DATA_SIZE) {
                data = data.subList(0, ResponseData.MAX_DATA_SIZE);
            }
            return data;
        }
        return null;
    }

    public static class Data {
        public static final String URI = "uri";
        public static final String TYPED_LITERAL = "typed-literal";

        private String type;
        private String value;

        private String processValue(String value) {
            if (Utility.isInteger(value)) {
                return Utility.formatInteger(value);
            }
            return value;
        }

        public String getType() {
            return type;
        }

        public Data setType(String type) {
            this.type = type;
            return this;
        }

        public String getValue() {
            return value;
        }

        public Data setValue(String value) {
            this.value = processValue(value);
            return this;
        }

        public Data(String type, String value) {
            this.type = type;
            this.value = processValue(value);
        }
    }
}
