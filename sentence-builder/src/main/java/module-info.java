module com.oetd.sentencebuilder {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.naming;

    requires org.hibernate.orm.core;
    requires jakarta.persistence;

    opens com.oetd.sentencebuilder to javafx.fxml, org.hibernate.orm.core;

    exports com.oetd.sentencebuilder;
}