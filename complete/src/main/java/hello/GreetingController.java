package hello;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@SpringBootApplication
public class GreetingController {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedMethods("*")
                        .allowedOrigins("*");
            }
        };
    }
    public static void main(String[] args) {
        SpringApplication.run(GreetingController.class, args);
    }


    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/{token}")
    @SendTo("/topic/{token}")
    public String greeting(@DestinationVariable String token, String message) {
        Logger.getLogger(getClass().getName()).log(Level.INFO, String.format("Writing %s to %s", message, token));
        mirror(message, token);
        return message;
    }

    @RequestMapping(path="/broker/{token}")
    public String legacy(@PathVariable("token") String token,@RequestBody String payload) throws JSONException, UnsupportedEncodingException {
        String message= URLDecoder.decode(payload, "UTF-8");
        message=message.substring("message=".length());
        mirror(message, token);
        simpMessagingTemplate.convertAndSend("/topic/"+token, message);
        return "Sending "+payload+" to all listeners on "+token;
    }

    static PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    static {
        connectionManager.setMaxTotal(100);
    }

    static ConcurrentHashMap<String, Long> connectionFailures=new ConcurrentHashMap<>();

    private void mirror(String message, String channel) {
        try {
            loadSettings();
        } catch (Throwable t) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "UNABLE TO READ HOSTS CONFIGURATION FILE", t);
        }
        if (my_id != null && known_hosts != null) {
            String req = null;
            try {
                JSONObject obj = new JSONObject(message);
                if (!obj.has("mirrored_from")) {
                    obj.put("mirrored_from", my_id);
                    req = obj.toString();
                }
            } catch (Throwable t) {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "UNABLE TO PARSE JSON POST, NOT MIRRORING ", t);
            }

            if (req != null) {
                for (Broker b : known_hosts) {
                    try {
                        Long lastFailureTime = connectionFailures.get(b.address);
                        if (lastFailureTime == null)
                            lastFailureTime = Long.valueOf(-1);
                        if (lastFailureTime == -1 || System.currentTimeMillis() - lastFailureTime > 2000) {
                            CloseableHttpClient client = HttpClients.custom()
                                    .setConnectionManager(connectionManager) // shared connection manager
                                    .setConnectionManagerShared(true).build();
                            HttpPost httpPost = new HttpPost(b.address + "/broker/"+channel);

                            StringEntity entity = new StringEntity("message=" + URLEncoder.encode(req, "UTF-8"));
                            httpPost.setEntity(entity);
                            httpPost.setHeader("Content-type", "application/json");

                            CloseableHttpResponse response = client.execute(httpPost);
                            int statusCode = response.getStatusLine().getStatusCode();
                            if (statusCode != 200) {
                                throw new IOException("Got status " + statusCode + " with message " + response.toString());
                            }
                            HttpClientUtils.closeQuietly(response);
                            HttpClientUtils.closeQuietly(client);
                            Logger.getLogger(getClass().getName()).log(Level.FINE, "Mirrored " + message + " to channel " + channel + " on " + b);
                            connectionFailures.put(b.address, (long) -1);
                        } else {
                            Logger.getLogger(getClass().getName()).log(Level.FINE, "Mirror still in throttle mode, not sending to " + b);
                        }
                    } catch (Throwable t) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "UNABLE TO MIRROR MESSAGE TO " + b, t);
                        connectionFailures.put(b.address, System.currentTimeMillis());
                    }
                }
            }
        }
    }


    private static List<Broker> known_hosts = null;
    private static String my_id = null;

    private void loadSettings() throws IOException, ParserConfigurationException, SAXException {
        if (known_hosts != null && my_id != null)
            return;
        String s = System.getProperty("broker_config_file");
        if (s == null) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "UNABLE TO READ HOSTS CONFIGURATION FILE FROM -Dbroker_config_file");
            s = System.getenv("broker_config_file");
            if (s == null) {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "UNABLE TO READ HOSTS CONFIGURATION FILE FROM broker_config_file environment variable");
                return;
            }
        }

        File f = new File(s);
        if (!f.exists()) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "UNABLE TO READ HOSTS CONFIGURATION FILE FROM " + s);
            return;
        }
        Logger.getLogger(getClass().getName()).log(Level.INFO, "Parsing config file " + s);

        String content = FileUtils.readFileToString(f, Charset.forName("UTF-8"));
        List<Broker> others = new ArrayList<>();
        String myuser = parseBrokerSettings(content, others);
        if(myuser == null)
            throw new IOException("Did not find my id element in settings file "+s);
        my_id=myuser;
        known_hosts=others;
    }


    public static String parseBrokerSettings(String data, List<Broker> others) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setIgnoringElementContentWhitespace(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        InputSource is = new InputSource();
        is.setCharacterStream(new StringReader(data));

        Document doc = db.parse(is);
        doc.normalize();

        Element result = doc.getDocumentElement();
        NodeList plist = result.getElementsByTagName("my_id");

        String potential_id = null;
        if (plist.getLength() != 1)
            throw new IOException("Did not find exactly one my_id element");
        for (int i = 0; i < plist.getLength(); i++) {
            Node node = plist.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element myElement = (Element) node;
                potential_id = myElement.getTextContent();
            }
        }

        plist = result.getElementsByTagName("broker");

        for (int i = 0; i < plist.getLength(); i++) {
            Node node = plist.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element myElement = (Element) node;
                Broker b = new Broker();
                b.id = readElement(myElement, "id");
                b.address = readElement(myElement, "address");
                others.add(b);
            }
        }
        return potential_id;
    }

    static class Broker {
        String id;
        String address;

        @Override
        public String toString() {
            return "Broker{" +
                    "id='" + id + '\'' +
                    ", address='" + address + '\'' +
                    '}';
        }
    }


    private static String readElement(Element myElement, String elementname) throws IOException {
        NodeList elementsByTagName = myElement.getElementsByTagName(elementname);
        if (elementsByTagName == null || elementsByTagName.getLength() == 0) {
            String myElementtext = myElement.getTextContent();
            throw new IOException("No " + elementname + " found " + myElementtext);
        }
        final Node item = elementsByTagName.item(0);
        return item.getTextContent();
    }
}
