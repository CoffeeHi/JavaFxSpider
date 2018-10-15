package com.cx.coffeehi.spider.javafx;

import java.net.URL;

import com.cx.coffeehi.spider.utils.SpiderThread;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j;
import javafx.scene.Parent;
import javafx.scene.Scene;
/**
 * 主启动器
 *
 * @author chenxiang
 * @data 2018年10月15日
 */
@Log4j
public class MainLauncher extends Application {
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        try {
            primaryStage = stage;
            URL url = getClass().getClassLoader().getResource("fxml/MyScene.fxml");
            Parent root = FXMLLoader.load(url);
            primaryStage.setTitle("Zhihu Spider");
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        log.info("SpiderThread stop");
        SpiderThread.getInstance().shutdown();
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }
}
