package dev.swine.kubernetes.pagercontroller;

import dev.swine.kubernetes.pagercontroller.model.Message;
import dev.swine.kubernetes.pagercontroller.model.MessageList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.Controllers;
import io.kubernetes.client.extended.controller.DefaultController;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.DefaultRateLimitingQueue;
import io.kubernetes.client.extended.workqueue.RateLimitingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MessageController {
    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    private SharedInformerFactory sharedInformerFactory;
    private Controller[] controllers;
    private ExecutorService controllerThreadPool;

    private int workerCount = 2;
    private String controllerName = "messages-controller";

    public MessageController() {
        try (final KubernetesClient client = new DefaultKubernetesClient()) {
            CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                    .withVersion(Message.V1ALPHA1)
                    .withScope("Namespaced")
                    .withGroup(Message.RESOURCE_GROUP)
                    .withPlural(Message.RESOURCE_PLURAL)
                    .build();

            this.sharedInformerFactory = client.informers();
            RateLimitingQueue<Request> workQueue = new DefaultRateLimitingQueue<>(Executors.newSingleThreadExecutor());
            SharedIndexInformer<Message> informer = this.sharedInformerFactory.sharedIndexInformerForCustomResource(crdContext, Message.class, MessageList.class, 15 * 60 * 1000);
            log.info("Informer factory initialized.");

            informer.addEventHandler(
                    new ResourceEventHandler<Message>() {
                        @Override
                        public void onAdd(Message msg) {
                            workQueue.add(new Request(msg.getMetadata().getNamespace(), msg.getMetadata().getName()));
                        }

                        @Override
                        public void onUpdate(Message oldMsg, Message newMsg) {
                            workQueue.add(new Request(newMsg.getMetadata().getNamespace(), newMsg.getMetadata().getName()));
                        }

                        @Override
                        public void onDelete(Message msg, boolean deletedFinalStateUnknown) {
                            // skip
                        }
                    });
            sharedInformerFactory.startAllRegisteredInformers();

            MessageReconciler reconciler = new MessageReconciler(informer);
            DefaultController c = new DefaultController(
                    reconciler,
                    workQueue,
                    informer::hasSynced
            );

            c.setName(this.controllerName);
            c.setWorkerCount(this.workerCount);
            c.setWorkerThreadPool(
                    Executors.newScheduledThreadPool(
                            this.workerCount, Controllers.namedControllerThreadFactory(this.controllerName)));
            c.setReconciler(reconciler);
            this.controllers = new Controller[]{c};
            this.run();
            TimeUnit.MINUTES.sleep(15);
        } catch (InterruptedException interruptedException) {
            log.info("interrupted: {}", interruptedException.getMessage());
        }
    }

    public void run() {
        if (controllers.length == 0) {
            throw new RuntimeException("no controller registered in the manager..");
        }
        this.sharedInformerFactory.startAllRegisteredInformers();
        CountDownLatch latch = new CountDownLatch(controllers.length);
        this.controllerThreadPool = Executors.newFixedThreadPool(controllers.length);
        for (Controller controller : this.controllers) {
            controllerThreadPool.submit(
                    () -> {
                        controller.run();
                        latch.countDown();
                    });
        }
        try {
            log.debug("Controller-Manager {} bootstrapping..");
            latch.await();
        } catch (InterruptedException e) {
            log.error("Aborting controller-manager.", e);
        } finally {
            log.info("Controller-Manager {} exited");
        }
    }

    public void shutdown() {
        for (Controller controller : this.controllers) {
            controller.shutdown();
        }
        if (controllerThreadPool != null) {
            this.controllerThreadPool.shutdown();
        }
        this.sharedInformerFactory.stopAllRegisteredInformers();
    }

    public static void main(String[] args) throws InterruptedException {
        MessageController c = new MessageController();
        log.info("Created controller.");
    }
}


/**
 * log.info("Starting all registered informers");
 * sharedInformerFactory.startAllRegisteredInformers();
 * <p>
 * // nodeReconciler prints node information on events
 * MessageReconciler msgReconciler = new MessageReconciler((io.kubernetes.client.informer.SharedIndexInformer<Message>) msgInformer);
 * <p>
 * // Use builder library to construct a default controller.
 * Controller controller =
 * ControllerBuilder.defaultBuilder((dev.swine.kubernetes.pagercontroller.controller.builder.SharedInformerFactory) sharedInformerFactory)
 * .watch(
 * (workQueue) ->
 * ControllerBuilder.controllerWatchBuilder(Message.class, workQueue)
 * .withWorkQueueKeyFunc(
 * (Message msg) ->
 * new Request(msg.getMetadata().getName())) // optional, default to
 * .build())
 * .withReconciler(msgReconciler) // required, set the actual reconciler
 * .withName("node-printing-controller") // optional, set name for controller
 * .withWorkerCount(4) // optional, set worker thread count
 * .withReadyFunc(
 * msgInformer
 * ::hasSynced) // optional, only starts controller when the cache has synced up
 * .build();
 * <p>
 * // Use builder library to manage one or multiple controllers.
 * ControllerManager controllerManager =
 * ControllerBuilder.controllerManagerBuilder((dev.swine.kubernetes.pagercontroller.controller.builder.SharedInformerFactory) sharedInformerFactory)
 * .addController(controller)
 * .build();
 */

