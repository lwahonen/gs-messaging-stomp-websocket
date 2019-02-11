package hello;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@SpringBootApplication
public class GreetingController {

    @MessageMapping("/{name}")
    @SendTo("/topic/{name}")
    public Greeting greeting(@DestinationVariable String name, HelloMessage message) {
        Logger.getLogger(getClass().getName()).log(Level.INFO, String.format("Writing %s to %s", message.getMessage(), name));
        return new Greeting("Hello, " + HtmlUtils.htmlEscape(message.getMessage()) + "!");
    }

    @RequestMapping(path="/broker/{token}")
    public String index(@PathVariable("token") String token) {
        return "Greetings from "+token;
    }
}
