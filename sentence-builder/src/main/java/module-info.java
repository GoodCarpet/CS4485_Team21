module com.oetd.sentencebuilder {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;


    opens com.oetd.sentencebuilder to javafx.fxml;
    exports com.oetd.sentencebuilder;
}