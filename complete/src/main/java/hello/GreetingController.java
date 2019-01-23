package hello;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;

@Controller
public class GreetingController {

    @MessageMapping("/broker/{name}")
    @SendTo("/topic/greetings")
    public Greeting greeting(@DestinationVariable String name, HelloMessage message ) {
            return new Greeting("Hello, " + HtmlUtils.htmlEscape(message.getMessage()) + "!");
    }

}
