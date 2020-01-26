KUBECONFIG ?= $(pwd)/.kubeconfig
CHANNEL ?= test-building-operators

KIND_CLUSTER_NAME := pcj

MESSAGE := Test message from $(shell hostname) at $(shell date)

# from https://suva.sh/posts/well-documented-makefiles/
.PHONY: help
help:  ## Display this help
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n\nTargets:\n"} /^[a-zA-Z0-9_-]+:.*?##/ { printf "  \033[36m%-30s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

.PHONY: kind_deploy
kind_deploy: ## Ensures a local kind cluster is created and running
	kind create cluster --name $(KIND_CLUSTER_NAME) --image kindest/node:v1.14.10 || /bin/true

.PHONY: kind_destroy
kind_destroy: ## Destroys the kind cluster
	kind delete cluster --name $(KIND_CLUSTER_NAME)

.PHONY: install
install: ## Installs CRDs into the cluster
	kubectl apply -f ./crds

.PHONY: package
package: ## Build the project
	mvn package

.PHONE: message
message: ## Send a demo message
	echo '{"apiVersion":"pagercontroller.swine.dev/v1alpha1","kind":"Message","metadata":{"name":"message-$(shell date +%Y\-%m\-%d\-\-%H%M%S)"},"spec":{"channel":"$(CHANNEL)","text":"$(MESSAGE)"}}' | jq . | tee | kubectl apply -f -

.PHONY: run
run: package ## Run the controller
	mvn exec:java -Dexec.mainClass="dev.swine.kubernetes.pagercontroller.MessageController"

.PHONY: watch
watch: ## Watch messages
	watch -d -n 0.5 kubectl get messages -A


