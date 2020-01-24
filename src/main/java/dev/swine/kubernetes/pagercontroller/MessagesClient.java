package dev.swine.kubernetes.pagercontroller;

import dev.swine.kubernetes.pagercontroller.model.DoneableMessage;
import dev.swine.kubernetes.pagercontroller.model.Message;
import dev.swine.kubernetes.pagercontroller.model.MessageList;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceSubresourceStatus;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceSubresourceStatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.RawCustomResourceOperationsImpl;

public class MessagesClient{
    MixedOperation<Message, MessageList, DoneableMessage, Resource<Message, DoneableMessage>> client;
    CustomResourceDefinition crd;
    CustomResourceDefinitionContext context;
    public static final String CRD_KIND = "CustomResourceDefinition";
    RawCustomResourceOperationsImpl clientRaw;

    public MessagesClient(KubernetesClient client) {
        CustomResourceSubresourceStatus status = new CustomResourceSubresourceStatusBuilder().build();
        this.crd = new CustomResourceDefinitionBuilder()
                .withApiVersion(Message.CRD_API_VERSION)
                .withKind(CRD_KIND)
                .withNewMetadata()
                    .withName(Message.RESOURCE_PLURAL + "." +  Message.RESOURCE_GROUP)
                .endMetadata()
                .withNewSpec()
                    .withScope(Message.SCOPE)
                    .withGroup(Message.RESOURCE_GROUP)
                    .withVersion(Message.V1ALPHA1)
                    .withNewNames()
                        .withSingular(Message.RESOURCE_SINGULAR)
                        .withPlural(Message.RESOURCE_PLURAL)
                        .withKind(Message.RESOURCE_KIND)
                        .withListKind(Message.RESOURCE_LIST_KIND)
                    .endNames()
                    //.withNewSubresources()
                    //    .withStatus(status)
                    //.endSubresources()
                .endSpec().
                build();

        this.context = new CustomResourceDefinitionContext.Builder()
                .withGroup(Message.RESOURCE_GROUP)
                .withName(Message.RESOURCE_PLURAL + "." +  Message.RESOURCE_GROUP)
                .withPlural(Message.RESOURCE_PLURAL)
                .withScope(Message.SCOPE)
                .withVersion(Message.V1ALPHA1)
                .build();

        this.client = client.customResources(crd, Message.class, MessageList.class, DoneableMessage.class);

        this.clientRaw = client.customResource(context);
    }
}
