package dev.swine.kubernetes.pagercontroller;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerManager;
import io.kubernetes.client.extended.controller.LeaderElectingController;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.extended.leaderelection.LeaderElector;
import io.kubernetes.client.extended.leaderelection.resourcelock.EndpointsLock;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.util.CallGeneratorParams;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import okhttp3.OkHttpClient;

import java.io.FileReader;

public class NodeControllerExample {
    private static final Logger log = LoggerFactory.getLogger(NodeControllerExample.class);


    public static void main(String[] args) throws IOException, ApiException {
        log.info("starting node controller example");

        // file path to your KubeConfig
        String kubeConfigPath = System.getenv("KUBECONFIG");
        if (kubeConfigPath == null) {
            kubeConfigPath = System.getProperty("user.home") + "/.kube/config";
        }
        log.info("using kubeconfig: " + kubeConfigPath);

        // TODO: detect in-cluster


        ApiClient apiClient =
                ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
        // set the global default api-client to the in-cluster one from above
        Configuration.setDefaultApiClient(apiClient);

        // loading the out-of-cluster config, a kubeconfig from file-system
        CoreV1Api coreV1Api = new CoreV1Api();
        OkHttpClient httpClient =
                apiClient.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
        apiClient.setHttpClient(httpClient);

        // instantiating an informer-factory, and there should be only one informer-factory globally.
        SharedInformerFactory informerFactory = new SharedInformerFactory();
        // registering node-informer into the informer-factory.
        SharedIndexInformer<V1Node> nodeInformer =
                informerFactory.sharedIndexInformerFor(
                        (CallGeneratorParams params) -> {
                            return coreV1Api.listNodeCall(
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    params.resourceVersion,
                                    params.timeoutSeconds,
                                    params.watch,
                                    null);
                        },
                        V1Node.class,
                        V1NodeList.class);
        informerFactory.startAllRegisteredInformers();

        // nodeReconciler prints node information on events
        NodePrintingReconciler nodeReconciler = new NodePrintingReconciler(nodeInformer);

        // Use builder library to construct a default controller.
        Controller controller =
                ControllerBuilder.defaultBuilder(informerFactory)
                        .watch(
                                (workQueue) ->
                                        ControllerBuilder.controllerWatchBuilder(V1Node.class, workQueue)
                                                .withWorkQueueKeyFunc(
                                                        (V1Node node) ->
                                                                new Request(node.getMetadata().getName())) // optional, default to
                                                .build())
                        .withReconciler(nodeReconciler) // required, set the actual reconciler
                        .withName("node-printing-controller") // optional, set name for controller
                        .withWorkerCount(4) // optional, set worker thread count
                        .withReadyFunc(
                                nodeInformer
                                        ::hasSynced) // optional, only starts controller when the cache has synced up
                        .build();

        // Use builder library to manage one or multiple controllers.
        ControllerManager controllerManager =
                ControllerBuilder.controllerManagerBuilder(informerFactory)
                        .addController(controller)
                        .build();

        // build a unique identity based on hostname + UUID
        String identity =  InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();

        LeaderElectingController leaderElectingController =
                new LeaderElectingController(
                        new LeaderElector(
                                new LeaderElectionConfig(
                                        new EndpointsLock("kube-system", "node-controller-example-leader-election", identity),
                                        Duration.ofMillis(10000),
                                        Duration.ofMillis(8000),
                                        Duration.ofMillis(5000))),
                        controllerManager);

        leaderElectingController.run();
    }

    static class NodePrintingReconciler implements Reconciler {

        private Lister<V1Node> nodeLister;

        public NodePrintingReconciler(SharedIndexInformer<V1Node> nodeInformer) {
            this.nodeLister = new Lister<>(nodeInformer.getIndexer());
        }

        @Override
        public Result reconcile(Request request) {
            V1Node node = this.nodeLister.get(request.getName());
            log.info("triggered reconciling " + node.getMetadata().getName());
            return new Result(false);
        }
    }
}
