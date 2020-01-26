# pager-controller-java

This is a sample controller to evaluate Java ecosystem for writing custom
operators

## Usage

### Prerequisites

- Java + Maven
- Kubernetes 1.12+

### Prepare cluster

```
# Create a local kind cluster
make kind_deploy

# Install CRDs into the cluster
make install
```

### Run controller

```
make run
```

### Send test message

```
make message "MESSAGE=Hello world"
```
