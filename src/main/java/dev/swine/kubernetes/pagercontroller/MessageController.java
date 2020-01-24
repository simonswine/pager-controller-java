package dev.swine.kubernetes.pagercontroller;

import dev.swine.kubernetes.pagercontroller.model.DoneableMessage;
import dev.swine.kubernetes.pagercontroller.model.Message;
import dev.swine.kubernetes.pagercontroller.model.MessageList;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.Controllers;
import io.kubernetes.client.extended.controller.DefaultController;
import io.kubernetes.client.extended.controller.LeaderElectingController;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.extended.leaderelection.LeaderElector;
import io.kubernetes.client.extended.leaderelection.resourcelock.EndpointsLock;
import io.kubernetes.client.extended.workqueue.DefaultRateLimitingQueue;
import io.kubernetes.client.extended.workqueue.RateLimitingQueue;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.ClientBuilder;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MessageController implements Controller{
    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    private SharedInformerFactory sharedInformerFactory;
    private Controller[] controllers;
    private ExecutorService controllerThreadPool;

    private int workerCount = 2;
    private String controllerName = "messages-controller";

    public MessageController(KubernetesClient client) {
        this.sharedInformerFactory = client.informers();

        MessagesClient crd = new MessagesClient(client);

        RateLimitingQueue<Request> workQueue = new DefaultRateLimitingQueue<>(Executors.newSingleThreadExecutor());
        SharedIndexInformer<Message> informer = this.sharedInformerFactory.sharedIndexInformerForCustomResource(crd.context, Message.class, MessageList.class, 15 * 60 * 1000);
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

        MessageReconciler reconciler = new MessageReconciler(informer, crd);
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
            log.debug("Controller-Manager {} bootstrapping..", this.controllerName);
            latch.await();
        } catch (InterruptedException e) {
            log.error("Aborting controller-manager.", e);
        } finally {
            log.info("Controller-Manager {} exited", this.controllerName);
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

    public static void main(String[] args) throws IOException {
        // build fabric8 client
        KubernetesClient client = new DefaultKubernetesClient();

        // check if we are conneted
        VersionInfo version = client.getVersion();
        log.info("Connected to API server {}", version.getGitVersion());

        // build upstream client
        ApiClient apiClient = ClientBuilder.defaultClient();
        Configuration.setDefaultApiClient(apiClient);
        OkHttpClient httpClient =
                apiClient.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
        apiClient.setHttpClient(httpClient);

        // build a unique identity based on hostname + UUID
        String identity =  InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();

        MessageController c = new MessageController(client);
        log.info("Created controller.");


        LeaderElectingController leaderElectingController =
                new LeaderElectingController(
                        new LeaderElector(
                                new LeaderElectionConfig(
                                        new EndpointsLock(
                                                "kube-system",
                                                String.format("%s-leader-election", c.controllerName),
                                                identity),
                                        Duration.ofMillis(10000),
                                        Duration.ofMillis(8000),
                                        Duration.ofMillis(5000))),
                        c);

        leaderElectingController.run();
    }
}
