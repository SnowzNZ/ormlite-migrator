package dev.snowz.ormlitemigrator;

public class DatabaseFieldType {

    private final String fieldName;
    private final String type;

    public DatabaseFieldType(final String fieldName, final String type) {
        this.fieldName = fieldName;
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public String getFieldName() {
        return fieldName;
    }

    @Override
    public String toString() {
        return "DatabaseFieldType{" +
            "fieldName='" + fieldName + '\'' +
            ", type='" + type + '\'' +
            '}';
    }
}
