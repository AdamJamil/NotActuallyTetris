package com.jamil;

import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class Main extends Application
{
    private static final int[] edges = new int[]{800, 1000, 1500, 2300, 3000, 5000};
    private static final boolean showcaseMode = false; //use for "showing off" the program, like a demo mode
    static final String[] inputs = new String[]{"t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9",
            "t", "o", "i", "s", "z", "l", "r"}; //defines all input nodes for the NN
    static final String[] outputs = new String[]{"o0", "o1", "o2", "o3", "p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9"}; //defines all output nodes for the NN
    synchronized

    @Override
    public void start(Stage primaryStage) throws Exception
    {
        primaryStage.setTitle("TENN");
        Group root = new Group();
        Canvas canvas = new Canvas(500, 350);
        root.getChildren().add(canvas);
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.BLACK); //everything will be black, not worth alternating colors because of performance drop

        new ShowcaseTrainer(canvas, inputs.length, outputs.length);
    }

    public static void main(String[] args)
    {
        if (showcaseMode)
            launch(args);
        else
            for (int i = 0; i < edges.length; i++)
                for (int j = 0; j < 5; j++)
                {
                    new Trainer(edges[i], inputs.length, outputs.length);
                }
        /*else for (int i = 0; i < 100; i++)
        {
            final int temp = i; //since it is accessed by a run method inside the lambda, this needs to be final
            //new Thread(() -> new Trainer(temp, inputs.length, outputs.length)).start(); //lambda expression with hardcoded inputs and outputs
        }*/
    }
}









