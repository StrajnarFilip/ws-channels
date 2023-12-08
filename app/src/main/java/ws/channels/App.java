package ws.channels;

import io.javalin.Javalin;
import io.javalin.websocket.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class App {
  private static final String CHANNEL_KEY = "channel";
  private static final Logger logger = Logger.getLogger("App");
  private static final ConcurrentMap<String, CopyOnWriteArrayList<WsContext>> channelSubscribers =
      new ConcurrentHashMap<>();

  public static void main(String[] args) {
    var app =
        Javalin.create(
                config -> {
                  config.plugins.enableCors(
                      cors -> {
                        cors.add(c -> c.anyHost());
                      });
                })
            .start("0.0.0.0", 7070);

    app.ws(
        "/channels/{channel}",
        ws -> {
          ws.onConnect(App::onConnect);
          ws.onMessage(App::onMessage);
          ws.onBinaryMessage(App::onBinaryMessage);
          ws.onClose(App::onClose);
          ws.onError(App::onError);
        });
  }

  private static void onError(WsErrorContext ctx) {
    try {
      String channelName = ctx.pathParam(CHANNEL_KEY);
      logger.log(Level.INFO, "An error has occurred with a client on channel {0}.", channelName);
      var subscribers = channelSubscribers.get(channelName);
      for (WsContext subscriber : subscribers) {
        if (subscriber.session.equals(ctx.session)) {
          subscribers.remove(subscriber);
        }
      }
    } catch (Exception e) {
      logger.severe(e.getClass().getSimpleName());
    }
  }

  private static void onClose(WsCloseContext ctx) {
    try {
      String channelName = ctx.pathParam(CHANNEL_KEY);
      logger.log(Level.INFO, "Subscriber closed connection on channel {0}.", channelName);
      var subscribers = channelSubscribers.get(channelName);
      for (WsContext subscriber : subscribers) {
        if (subscriber.session == ctx.session) {
          subscribers.remove(subscriber);
        }
      }
    } catch (Exception e) {
      logger.severe(e.getClass().getSimpleName());
    }
  }

  private static void onBinaryMessage(WsBinaryMessageContext ctx) {
    String channelName = ctx.pathParam(CHANNEL_KEY);
    logger.log(Level.INFO, "New binary message on channel {0}.", channelName);
    try {
      for (WsContext subscriber : channelSubscribers.get(channelName)) {
        if (subscriber.session == ctx.session) subscriber.send(ctx.data());
      }
    } catch (Exception e) {
      logger.severe(e.getClass().getSimpleName());
    }
  }

  private static void onMessage(WsMessageContext ctx) {
    try {
      String channelName = ctx.pathParam(CHANNEL_KEY);
      logger.log(Level.INFO, "New message on channel {0}.", channelName);
      for (WsContext subscriber : channelSubscribers.get(channelName)) {
        if (subscriber.session.isOpen()) {
          subscriber.send(ctx.message());
        }
      }
    } catch (Exception e) {
      logger.severe(e.getClass().getSimpleName());
    }
  }

  private static void onConnect(WsConnectContext ctx) {
    try {
      String channelName = ctx.pathParam(CHANNEL_KEY);
      logger.log(Level.INFO, "New subscriber on channel {0}.", channelName);
      List<WsContext> subscribers = channelSubscribers.get(channelName);
      if (subscribers == null) {
        var freshList = new CopyOnWriteArrayList<WsContext>();
        freshList.add(ctx);
        channelSubscribers.put(channelName, freshList);
      } else {
        subscribers.add(ctx);
      }
    } catch (Exception e) {
      logger.severe(e.getClass().getSimpleName());
    }
  }
}
