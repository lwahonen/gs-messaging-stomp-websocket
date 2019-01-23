package hello;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;

import java.util.logging.Level;
import java.util.logging.Logger;

@Controller
public class GreetingController {

    @MessageMapping("/broker/{name}")
    @SendTo("/topic/{name}")
    public Greeting greeting(@DestinationVariable String name, HelloMessage message) {
        Logger.getLogger(getClass().getName()).log(Level.INFO, String.format("Writing %s to %s", message.getMessage(), name));
        return new Greeting("Hello, " + HtmlUtils.htmlEscape(message.getMessage()) + "!");
    }

}
