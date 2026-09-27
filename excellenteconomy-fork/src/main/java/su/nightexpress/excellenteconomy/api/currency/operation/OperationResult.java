package su.nightexpress.excellenteconomy.api.currency.operation;

public enum OperationResult {

    SUCCESS, FAILURE;

    public boolean bool() {
        return this.success();
    }

    public boolean success() {
        return this == SUCCESS;
    }

    public boolean failure() {
        return this == FAILURE;
    }
}
