package dev.swine.kubernetes.pagercontroller;

import dev.swine.kubernetes.pagercontroller.model.Message;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageReconciler implements Reconciler {
    private static final Logger log = LoggerFactory.getLogger(MessageReconciler.class);

    private Lister<Message> msgLister;

    public MessageReconciler(SharedIndexInformer<Message> msgInformer) {
        this.msgLister = new Lister<>(msgInformer.getIndexer());
    }

    @Override
    public Result reconcile(Request request) {
        log.info(String.format("triggered reconciling %s/%s", request.getNamespace(),request.getName()));
        Message msg = this.msgLister.namespace(request.getNamespace()).get(request.getName());
        log.info(String.format("message is channel=%s text=%30s", msg.getSpec().getChannel(), msg.getSpec().getText()));
        return new Result(false);
    }
}
