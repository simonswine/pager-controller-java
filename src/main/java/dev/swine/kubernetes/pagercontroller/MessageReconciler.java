package dev.swine.kubernetes.pagercontroller;

import dev.swine.kubernetes.pagercontroller.model.Message;
import dev.swine.kubernetes.pagercontroller.model.MessageSpec;
import dev.swine.kubernetes.pagercontroller.model.MessageState;
import dev.swine.kubernetes.pagercontroller.model.MessageStatus;
import dev.swine.kubernetes.pagercontroller.tlkio.TlkIo;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class MessageReconciler implements Reconciler {
    private static final Logger log = LoggerFactory.getLogger(MessageReconciler.class);

    private Lister<Message> msgLister;

    private ConcurrentHashMap<String, TlkIo> clients;
    private MessagesClient mc;

    public MessageReconciler(SharedIndexInformer<Message> msgInformer, MessagesClient mc) {
        this.msgLister = new Lister<>(msgInformer.getIndexer());
        this.clients = new ConcurrentHashMap<String, TlkIo>();
        this.mc = mc;
    }

    @Override
    public Result reconcile(Request request) {
        String namespace = request.getNamespace();
        String name = request.getName();
        log.info(String.format("triggered reconciling %s/%s", namespace, name));
        Message msg = this.msgLister.namespace(namespace).get(name);

        try {
            // default to new messages
            if (msg.getSpec().getState() == null) {
                msg.getSpec().setState(MessageState.NEW);
                mc.clientRaw.edit(namespace, name, msg.toString());
                return new Result(false);
            }

            // ignore message which are not new
            if (msg.getSpec().getState() != MessageState.NEW) {
                log.debug(String.format("skip %s/%s as its not in state New", namespace, name));
                return new Result(false);
            }

            // ignore messages which have failed more than 5 times
            if (msg.getStatus() != null && msg.getStatus().getErrorCount() >= 5) {
                log.debug(String.format("skip %s/%s due to its to high error count", namespace, name));
                return new Result(false);
            }

            // get our tlkio client
            String key = msg.getSpec().getChannel();
            TlkIo c = this.clients.get(key);
            if (c == null) {
                c = new TlkIo(msg.getSpec().getChannel(), "tlkio-controller");
                this.clients.put(key, c);
            }

            // try sending the message to the channel
            try {
                c.say(msg.getSpec().getText());
                log.info("sent message {}/{} to channel={}", request.getNamespace(), request.getName(), msg.getSpec().getChannel());
                msg.getSpec().setState(MessageState.SENT);
            } catch (IOException e) {
                log.warn("failed sending message {}/{} to channel={}: {}", e.getMessage());
                if (msg.getStatus() == null){
                    msg.setStatus(new MessageStatus());
                }
                msg.getStatus().setErrorCount(msg.getStatus().getErrorCount() + 1);
                mc.clientRaw.edit(namespace, name, msg.toString());
                return new Result(true);
            }

            mc.clientRaw.edit(namespace, name, msg.toString());
            return new Result(false);
        } catch (Exception e) {
            e.printStackTrace();
            log.warn(e.toString());
            return new Result(true);
        }
    }
}
