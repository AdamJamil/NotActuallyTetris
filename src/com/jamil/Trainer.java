package com.jamil;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.event.EventHandler;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;

import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

class Trainer
{
    private Timeline timeline;
    private static final int networksPerGeneration = 100;

    private long totalFrames = 0;
    private NeuralNetwork[] generation = new NeuralNetwork[networksPerGeneration];
    private ArrayList<NeuralNetwork> orderedNeuralNetworks = new ArrayList<>(networksPerGeneration);
    private int currentNeuralNetworkIndex, currentGeneration;
    private NeuralNetwork neuralNetwork;
    private int inputs, outputs;
    private double width, height;
    private long t = 0;

    private int[][] field = new int[10][20];
    private int[] topology = new int[10];
    private int[] tallest = new int[10];
    private int fieldHeight = 0;
    private int orientation = 0;
    private int nextBlock;
    private int currentBlock;
    private int linesCleared = 0;
    private int piecesDropped = 0;

    Color red = Color.RED;
    Color orange = Color.ORANGE;
    Color yellow = Color.YELLOW;
    Color green = Color.GREEN;
    Color cyan = Color.CYAN;
    Color blue = Color.BLUE;
    Color magenta = Color.MAGENTA;
    Color black = Color.BLACK;
    Color white=Color.WHITE;

    int movingPos = 3;

    private static final int nodes = 5;
    int edges = -1;

    private NeuralNetwork finalNet;
    private void initializePrimaryGeneration()
    {
        for (int i = 0; i < networksPerGeneration; i++)
        {
            short size = (short) (31 + (Math.random() * 10));

            NodeGene[] inputNodeGenes = new NodeGene[nodes];
            EdgeGene[] inputEdgeGenes = new EdgeGene[edges];

            for (int j = 0; j < nodes; j++)
                inputNodeGenes[j] = NodeGene.randomNodeGene(inputs, outputs, size);

            for (int j = 0; j < edges; j++)
                inputEdgeGenes[j] = EdgeGene.randomEdgeGene(size);

            generation[i] = new NeuralNetwork(inputs, outputs, inputNodeGenes, inputEdgeGenes, size);
        }
        neuralNetwork = generation[0];
    }

    private void createNextGeneration()
    {
        //top 1 - 5: 1 clone, 3 mutations : 20
        //top 6 - 20: 4 mutations         : 60
        //top 21 - 30: 2 mutations        : 20
        //top 31 - 50: 1 mutation         : 20
        for (int i = 0; i < 5; i++)
        {
            orderedNeuralNetworks.get(i).clean();
            generation[i * 3] = orderedNeuralNetworks.get(i);
            generation[i * 3 + 1] = orderedNeuralNetworks.get(i).mutate();
            generation[i * 3 + 2] = orderedNeuralNetworks.get(i).mutate();
            generation[i * 3 + 3] = orderedNeuralNetworks.get(i).mutate();
        }

        for (int i = 5; i < 20; i++)
        {
            generation[i * 3 + 5] = orderedNeuralNetworks.get(i).mutate();
            generation[i * 3 + 6] = orderedNeuralNetworks.get(i).mutate();
            generation[i * 3 + 7] = orderedNeuralNetworks.get(i).mutate();
            generation[i * 3 + 8] = orderedNeuralNetworks.get(i).mutate();
        }

        for (int i = 20; i < 30; i++)
        {
            generation[i * 2 + 20] = orderedNeuralNetworks.get(i).mutate();
            generation[i * 2 + 21] = orderedNeuralNetworks.get(i).mutate();
        }

        for (int i = 30; i < 50; i++)
            generation[i + 50] = orderedNeuralNetworks.get(i).mutate();
    }

    Trainer(int edges, int inputs, int outputs)
    {
        this.inputs = inputs;
        this.outputs = outputs;
        this.edges = edges;

        initializePrimaryGeneration();

        final XoRoShiRo128PlusRandom decider = new XoRoShiRo128PlusRandom();
        decider.setSeed(0);

        //boilerplate code
        timeline = new Timeline();
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.setAutoReverse(false);

        currentBlock = ((int) (decider.nextDouble()*7))+1;
        nextBlock = ((int) (decider.nextDouble()*7))+1;

        while (true)
        {
            try
            {
                if (neuralNetwork.dead)
                    generation[-1].fitness--;

                computeTopology();

                if (movingPos < 0)
                    movingPos = 0;

                clearLines();

                computeTopology();
                double[] input = new double[]{topology[0], topology[1], topology[2], topology[3], topology[4], topology[5], topology[6], topology[7], topology[8], topology[9], -1, -1, -1, -1, -1, -1, -1};
                input[currentBlock + 9] += 2;
                double[] output = neuralNetwork.execute(input);
                double max = -9999999f;
                int index = -1;
                for (int i = 0; i < 4; i++)
                    if (output[i] > max)
                    {
                        max = output[i];
                        index = i;
                    }
                orientation = index;
                index = -1;
                max = -9999999f;
                for (int i = 4; i < output.length; i++)
                    if (output[i] > max)
                    {
                        max = output[i];
                        index = i;
                    }
                movingPos = index - 4;

                tBlock(movingPos, orientation);
                oBlock(movingPos);
                iBlock(movingPos, orientation);
                sBlock(movingPos, orientation);
                zBlock(movingPos, orientation);
                lBlock(movingPos, orientation);
                rBlock(movingPos, orientation);
                piecesDropped++;
                currentBlock = nextBlock;
                nextBlock = ((int) (decider.nextDouble() * 7)) + 1;
            }
            catch (ArrayIndexOutOfBoundsException e)
            {
                //e.printStackTrace();
                neuralNetwork.fitness = piecesDropped + 4 * linesCleared;

                decider.setSeed(0);
                currentBlock = ((int) (decider.nextDouble() * 7)) + 1;
                nextBlock = ((int) (decider.nextDouble() * 7)) + 1;
                linesCleared = 0;
                piecesDropped = 0;
                for (int i = 0; i < field.length; i++)
                    for (int j = 0; j < field[0].length; j++)
                        field[i][j] = 0;
                orientation = 0;
                movingPos = 0;
                for (int i = 0; i < topology.length; i++)
                {
                    tallest[i] = 0;
                    topology[i] = 0;
                }
                fieldHeight = 0;

                orderedNeuralNetworks.add(neuralNetwork);

                currentNeuralNetworkIndex++;
                if (currentNeuralNetworkIndex == 100)
                {
                    Collections.sort(orderedNeuralNetworks);
                    currentNeuralNetworkIndex = 0;
                    currentGeneration++;
                    //System.out.println("generation: " + currentGeneration + ",  best: " + orderedNeuralNetworks.get(0).fitness);
                    if (currentGeneration == 10000)
                    {
                        System.out.println(edges + ": " + orderedNeuralNetworks.get(0).fitness);
                        break;
                    }

                    if (orderedNeuralNetworks.get(0).fitness >= 10000)
                    {
                        finalNet = orderedNeuralNetworks.get(0);
                        break;
                    }

                    createNextGeneration();
                    orderedNeuralNetworks.clear();
                }

                neuralNetwork = generation[currentNeuralNetworkIndex];
            }
        }
    }


    private void tBlock(int outputPosition, int orientation)
    {
        if (currentBlock == 1) //t block
        {
            if (orientation == 0)
            {
                if (outputPosition == 8 || outputPosition == 9)
                    outputPosition = 7;
                //if the small bit at the bottom is going to hit first
                if (topology[outputPosition + 1] >= topology[outputPosition] && topology[outputPosition + 1] >= topology[outputPosition + 2])
                {
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight + 1] = 1;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 1] = 1;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 1;
                    field[outputPosition + 2][topology[outputPosition + 1] + fieldHeight + 1] = 1;
                }
                //otherwise, one of the "wings" will hit first. if it's the left wing:
                else if (topology[outputPosition] > topology[outputPosition + 2])
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 1;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 1;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight - 1] = 1;
                    field[outputPosition + 2][topology[outputPosition] + fieldHeight] = 1;
                }
                //now it's the right wing
                else
                {
                    field[outputPosition][topology[outputPosition + 2] + fieldHeight] = 1;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight] = 1;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight - 1] = 1;
                    field[outputPosition + 2][topology[outputPosition + 2] + fieldHeight] = 1;
                }
            }
            else if (orientation == 1)
            {
                if (outputPosition == 9)
                    outputPosition = 8;
                //if the small bit at the bottom is going to hit first
                if (topology[outputPosition + 1] >= topology[outputPosition])
                {
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight + 1] = 1;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 2] = 1;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 1] = 1;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 1;
                }
                //else if the left wing hits first
                else
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 1;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight + 1] = 1;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 1;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight - 1] = 1;
                }
            }
            else if (orientation == 2)
            {
                if (outputPosition == 8 || outputPosition == 9)
                    outputPosition = 7;
                //if the left wing will hit first
                if (topology[outputPosition] >= topology[outputPosition + 1] && topology[outputPosition] >= topology[outputPosition + 2])
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 1;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight + 1] = 1;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 1;
                    field[outputPosition + 2][topology[outputPosition] + fieldHeight] = 1;
                }
                //if the middle will hit first
                else if (topology[outputPosition + 1] >= topology[outputPosition + 2])
                {
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 1;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 1] = 1;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 1;
                    field[outputPosition + 2][topology[outputPosition + 1] + fieldHeight] = 1;
                }
                //if the right
                else
                {
                    field[outputPosition][topology[outputPosition + 2] + fieldHeight] = 1;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight + 1] = 1;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight] = 1;
                    field[outputPosition + 2][topology[outputPosition + 2] + fieldHeight] = 1;
                }
            }
            else if (orientation == 3)
            {
                if (outputPosition == 9)
                    outputPosition = 8;
                //if the small bit at the bottom is going to hit first
                if (topology[outputPosition] >= topology[outputPosition + 1])
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 1;
                    field[outputPosition][topology[outputPosition] + fieldHeight + 1] = 1;
                    field[outputPosition][topology[outputPosition] + fieldHeight + 2] = 1;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight + 1] = 1;
                }
                //else if the right wing hits first
                else
                {
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 1;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight + 1] = 1;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 1;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight - 1] = 1;
                }
            }
        }
    }

    private void oBlock(int outputPosition)
    {
        if (currentBlock == 2)
        {
            if (outputPosition == 9)
                outputPosition = 8;

            //if the left hits first
            if (topology[outputPosition] >= topology[outputPosition + 1])
            {
                field[outputPosition][topology[outputPosition] + fieldHeight] = 2;
                field[outputPosition][topology[outputPosition] + fieldHeight + 1] = 2;
                field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 2;
                field[outputPosition + 1][topology[outputPosition] + fieldHeight + 1] = 2;
            }
            //else if the right hits
            else
            {
                field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 2;
                field[outputPosition][topology[outputPosition + 1] + fieldHeight + 1] = 2;
                field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 2;
                field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 1] = 2;
            }
        }
    }

    private void iBlock(int outputPosition, int orientation)
    {
        if (currentBlock == 3)
        {
            orientation %= 2;
            if (orientation == 0)
            {
                if (outputPosition == 7 || outputPosition == 8 || outputPosition == 9)
                    outputPosition = 6;
                //if first one
                if (topology[outputPosition] >= topology[outputPosition + 1] && topology[outputPosition] >= topology[outputPosition + 2] && topology[outputPosition] >= topology[outputPosition + 3])
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 3;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 3;
                    field[outputPosition + 2][topology[outputPosition] + fieldHeight] = 3;
                    field[outputPosition + 3][topology[outputPosition] + fieldHeight] = 3;
                }
                else if (topology[outputPosition + 1] >= topology[outputPosition + 2] && topology[outputPosition + 1] >= topology[outputPosition + 3])
                {
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 3;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 3;
                    field[outputPosition + 2][topology[outputPosition + 1] + fieldHeight] = 3;
                    field[outputPosition + 3][topology[outputPosition + 1] + fieldHeight] = 3;
                }
                else if (topology[outputPosition + 2] >= topology[outputPosition + 3])
                {
                    field[outputPosition][topology[outputPosition + 2] + fieldHeight] = 3;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight] = 3;
                    field[outputPosition + 2][topology[outputPosition + 2] + fieldHeight] = 3;
                    field[outputPosition + 3][topology[outputPosition + 2] + fieldHeight] = 3;
                }
                else
                {
                    field[outputPosition][topology[outputPosition + 3] + fieldHeight] = 3;
                    field[outputPosition + 1][topology[outputPosition + 3] + fieldHeight] = 3;
                    field[outputPosition + 2][topology[outputPosition + 3] + fieldHeight] = 3;
                    field[outputPosition + 3][topology[outputPosition + 3] + fieldHeight] = 3;
                }
            }
            else if (orientation == 1)
            {
                field[outputPosition][topology[outputPosition] + fieldHeight] = 3;
                field[outputPosition][topology[outputPosition] + fieldHeight + 1] = 3;
                field[outputPosition][topology[outputPosition] + fieldHeight + 2] = 3;
                field[outputPosition][topology[outputPosition] + fieldHeight + 3] = 3;
            }
        }
    }

    private void sBlock(int outputPosition, int orientation)
    {
        if (currentBlock == 4)
        {
            orientation %= 2;
            if (orientation == 0)
            {
                if (outputPosition == 8 || outputPosition == 9)
                    outputPosition = 7;
                //left
                if (topology[outputPosition] >= topology[outputPosition + 2] && topology[outputPosition] >= topology[outputPosition + 1])
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 4;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight + 1] = 4;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 4;
                    field[outputPosition + 2][topology[outputPosition] + fieldHeight + 1] = 4;
                }
                //middle
                else if (topology[outputPosition + 1] >= topology[outputPosition + 2])
                {
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 4;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 1] = 4;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 4;
                    field[outputPosition + 2][topology[outputPosition + 1] + fieldHeight + 1] = 4;
                }
                //right
                else
                {
                    field[outputPosition][topology[outputPosition + 2] + fieldHeight - 1] = 4;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight - 1] = 4;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight] = 4;
                    field[outputPosition + 2][topology[outputPosition + 2] + fieldHeight] = 4;
                }
            }
            else
            {
                if (outputPosition == 9)
                    outputPosition = 8;
                //right side
                if (topology[outputPosition + 1] >= topology[outputPosition])
                {
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 4;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 1] = 4;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight + 1] = 4;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight + 2] = 4;
                }
                //left
                else
                {
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight - 1] = 4;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 4;
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 4;
                    field[outputPosition][topology[outputPosition] + fieldHeight + 1] = 4;
                }
            }
        }
    }

    private void zBlock(int outputPosition, int orientation)
    {
        if (currentBlock == 5)
        {
            orientation %= 2;
            if (orientation == 0)
            {
                if (outputPosition == 8 || outputPosition == 9)
                    outputPosition = 7;
                //lower half first
                if (topology[outputPosition + 1] >= topology[outputPosition] || topology[outputPosition + 2] >= topology[outputPosition])
                {
                    //right side first
                    if (topology[outputPosition + 2] >= topology[outputPosition + 1])
                    {
                        field[outputPosition][topology[outputPosition + 2] + fieldHeight + 1] = 5;
                        field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight + 1] = 5;
                        field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight] = 5;
                        field[outputPosition + 2][topology[outputPosition + 2] + fieldHeight] = 5;
                    }
                    //middle first
                    else
                    {
                        field[outputPosition][topology[outputPosition + 1] + fieldHeight + 1] = 5;
                        field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 1] = 5;
                        field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 5;
                        field[outputPosition + 2][topology[outputPosition + 1] + fieldHeight] = 5;
                    }
                }
                //right first
                else
                {
                    field[outputPosition][topology[outputPosition + 2] + fieldHeight - 1] = 5;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight - 1] = 5;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight] = 5;
                    field[outputPosition + 2][topology[outputPosition + 2] + fieldHeight] = 5;
                }
            }
            else
            {
                if (outputPosition == 9)
                    outputPosition = 8;
                //left side
                if (topology[outputPosition] >= topology[outputPosition + 1])
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 5;
                    field[outputPosition][topology[outputPosition] + fieldHeight + 1] = 5;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight + 1] = 5;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight + 2] = 5;
                }
                //right
                else
                {
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 5;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 1] = 5;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 5;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight - 1] = 5;
                }
            }
        }
    }

    private void lBlock(int outputPosition, int orientation)
    {
        if (currentBlock == 6)
        {
            if (orientation == 0)
            {
                if (outputPosition == 8 || outputPosition == 9)
                    outputPosition = 7;
                //left
                if (topology[outputPosition] >= topology[outputPosition + 1] && topology[outputPosition] >= topology[outputPosition + 2])
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 6;
                    field[outputPosition][topology[outputPosition] + fieldHeight + 1] = 6;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight + 1] = 6;
                    field[outputPosition + 2][topology[outputPosition] + fieldHeight + 1] = 6;
                }
                //middle
                else if (topology[outputPosition + 1] >= topology[outputPosition + 2])
                {
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight - 1] = 6;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 6;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 6;
                    field[outputPosition + 2][topology[outputPosition + 1] + fieldHeight] = 6;
                }
                //right
                else
                {
                    field[outputPosition][topology[outputPosition + 2] + fieldHeight - 1] = 6;
                    field[outputPosition][topology[outputPosition + 2] + fieldHeight] = 6;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight] = 6;
                    field[outputPosition + 2][topology[outputPosition + 2] + fieldHeight] = 6;
                }
            }
            else if (orientation == 1)
            {
                if (outputPosition == 9)
                    outputPosition = 8;
                //right
                if (topology[outputPosition + 1] + 1 >= topology[outputPosition])
                {
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight + 2] = 6;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 6;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 1] = 6;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 2] = 6;
                }
                //left
                else
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 6;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 6;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight - 1] = 6;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight - 2] = 6;
                }
            }
            else if (orientation == 2)
            {
                if (outputPosition == 8 || outputPosition == 9)
                    outputPosition = 7;
                //left
                if (topology[outputPosition] >= topology[outputPosition + 1] && topology[outputPosition] >= topology[outputPosition + 2])
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 6;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 6;
                    field[outputPosition + 2][topology[outputPosition] + fieldHeight] = 6;
                    field[outputPosition + 2][topology[outputPosition] + fieldHeight + 1] = 6;
                }
                //if the middle will hit first
                else if (topology[outputPosition + 1] >= topology[outputPosition + 2])
                {
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 6;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 6;
                    field[outputPosition + 2][topology[outputPosition + 1] + fieldHeight] = 6;
                    field[outputPosition + 2][topology[outputPosition + 1] + fieldHeight + 1] = 6;
                }
                //if the right
                else
                {
                    field[outputPosition][topology[outputPosition + 2] + fieldHeight] = 6;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight + 1] = 6;
                    field[outputPosition + 2][topology[outputPosition + 2] + fieldHeight] = 6;
                    field[outputPosition + 2][topology[outputPosition + 2] + fieldHeight + 1] = 6;
                }
            }
            else if (orientation == 3)
            {
                if (outputPosition == 9)
                    outputPosition = 8;
                //left
                if (topology[outputPosition] >= topology[outputPosition + 1])
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 6;
                    field[outputPosition][topology[outputPosition] + fieldHeight + 1] = 6;
                    field[outputPosition][topology[outputPosition] + fieldHeight + 2] = 6;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 6;
                }
                //right
                else
                {
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 6;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight + 1] = 6;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight + 2] = 6;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 6;
                }
            }
        }
    }

    private void rBlock(int outputPosition, int orientation)
    {
        if (currentBlock == 7)
        {
            if (orientation == 0)
            {
                if (outputPosition == 8 || outputPosition == 9)
                    outputPosition = 7;
                //right
                if (topology[outputPosition + 2] >= topology[outputPosition] && topology[outputPosition + 2] >= topology[outputPosition + 1])
                {
                    field[outputPosition][topology[outputPosition + 2] + fieldHeight + 1] = 7;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight + 1] = 7;
                    field[outputPosition + 2][topology[outputPosition + 2] + fieldHeight + 1] = 7;
                    field[outputPosition + 2][topology[outputPosition + 2] + fieldHeight] = 7;
                }
                //middle
                else if (topology[outputPosition + 1] >= topology[outputPosition])
                {
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 7;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 7;
                    field[outputPosition + 2][topology[outputPosition + 1] + fieldHeight] = 7;
                    field[outputPosition + 2][topology[outputPosition + 1] + fieldHeight - 1] = 7;
                }
                //left
                else
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 7;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 7;
                    field[outputPosition + 2][topology[outputPosition] + fieldHeight] = 7;
                    field[outputPosition + 2][topology[outputPosition] + fieldHeight - 1] = 7;
                }
            }
            else if (orientation == 1)
            {
                if (outputPosition == 9)
                    outputPosition = 8;
                //right
                if (topology[outputPosition + 1] >= topology[outputPosition])
                {
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 7;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 1] = 7;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight + 2] = 7;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 7;
                }
                //left
                else
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 7;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight + 1] = 7;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight + 2] = 7;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 7;
                }
            }
            else if (orientation == 2)
            {
                if (outputPosition == 8 || outputPosition == 9)
                    outputPosition = 7;
                //left
                if (topology[outputPosition] >= topology[outputPosition + 1] && topology[outputPosition] >= topology[outputPosition + 2])
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 7;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight] = 7;
                    field[outputPosition + 2][topology[outputPosition] + fieldHeight] = 7;
                    field[outputPosition][topology[outputPosition] + fieldHeight + 1] = 7;
                }
                //if the middle will hit first
                else if (topology[outputPosition + 1] >= topology[outputPosition + 2])
                {
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 7;
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 7;
                    field[outputPosition + 2][topology[outputPosition + 1] + fieldHeight] = 7;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight + 1] = 7;
                }
                //if the right
                else
                {
                    field[outputPosition][topology[outputPosition + 2] + fieldHeight] = 7;
                    field[outputPosition + 1][topology[outputPosition + 2] + fieldHeight] = 7;
                    field[outputPosition + 2][topology[outputPosition + 2] + fieldHeight] = 7;
                    field[outputPosition][topology[outputPosition + 2] + fieldHeight + 1] = 7;
                }
            }
            else if (orientation == 3)
            {
                if (outputPosition == 9)
                    outputPosition = 8;
                //left
                if (topology[outputPosition] + 1 >= topology[outputPosition + 1])
                {
                    field[outputPosition][topology[outputPosition] + fieldHeight + 2] = 7;
                    field[outputPosition][topology[outputPosition] + fieldHeight] = 7;
                    field[outputPosition][topology[outputPosition] + fieldHeight + 1] = 7;
                    field[outputPosition + 1][topology[outputPosition] + fieldHeight + 2] = 7;
                }
                //right
                else
                {
                    field[outputPosition + 1][topology[outputPosition + 1] + fieldHeight] = 7;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight] = 7;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight - 1] = 7;
                    field[outputPosition][topology[outputPosition + 1] + fieldHeight - 2] = 7;
                }
            }
        }
    }

    private void drawNext(GraphicsContext gc)
    {
        if (nextBlock == 1)
        {
            gc.setFill(magenta);
            gc.fillRect(443, 205, 15, 15);
            gc.fillRect(428, 220, 45, 15);
        }
        if (nextBlock == 2)
        {
            gc.setFill(yellow);
            gc.fillRect(435, 205, 30, 30);
        }
        if (nextBlock == 3)
        {
            gc.setFill(cyan);
            gc.fillRect(443, 190, 15, 60);
        }
        if (nextBlock == 4)
        {
            gc.setFill(red);
            gc.fillRect(450, 205, 30, 15);
            gc.fillRect(435, 220, 30, 15);
        }
        if (nextBlock == 5)
        {
            gc.setFill(green);
            gc.fillRect(435, 205, 30, 15);
            gc.fillRect(450, 220, 30, 15);
        }
        if (nextBlock == 6)
        {
            gc.setFill(blue);
            gc.fillRect(428, 205, 15, 30);
            gc.fillRect(428, 205, 45, 15);
        }
        if (nextBlock == 7)
        {
            gc.setFill(orange);
            gc.fillRect(457, 205, 15, 30);
            gc.fillRect(428, 205, 45, 15);
        }
    }

    private void computeTopology()
    {
        for (int i = 0; i < 10; i++)
            for (int j = 0; j < 20; j++)
                if (field[i][j] != 0)
                    tallest[i] = j;

        int sum = 0;
        for (int i = 0; i < 10; i++)
            sum = sum + tallest[i];
        fieldHeight = (sum / 10);
        for (int i = 0; i < 10; i++)
            topology[i] = tallest[i] - (sum / 10) + 1;
    }

    private void drawField(GraphicsContext gc)
    {
        for (int j = 19; j >= 0; j = j - 1)
        {
            for (int i = 0; i < 10; i++)
            {
                if (field[i][j] == 4)
                    gc.setFill(red);
                if (field[i][j] == 7)
                    gc.setFill(orange);
                if (field[i][j] == 2)
                    gc.setFill(yellow);
                if (field[i][j] == 5)
                    gc.setFill(green);
                if (field[i][j] == 3)
                    gc.setFill(cyan);
                if (field[i][j] == 6)
                    gc.setFill(blue);
                if (field[i][j] == 1)
                    gc.setFill(magenta);
                if (field[i][j] != 0)
                    gc.fillRect(i * 15 + 50, -j * 15 + 40 + (19 * 15), 15, 15);
            }
        }
    }

    private void drawLines(GraphicsContext gc)
    {
        gc.setFill(white);

        for (int i = 15 + 50; i < 150 + 50; i = i + 15)
            gc.fillRect(i, 25, 1, 300);
        for (int i = 15 + 25; i < 300 + 25; i = i + 15)
            gc.fillRect(50, i, 150, 1);
    }

    private void drawCurrent(GraphicsContext gc)
    {

        if (currentBlock == 1)
        {
            gc.setFill(magenta);
            if (orientation == 2)
            {
                if (movingPos == 8 || movingPos == 9)
                    movingPos = 7;
                gc.fillRect((443 - 428) + 50 + ((movingPos) * 15), 25, 15, 15);
                gc.fillRect(50 + ((movingPos) * 15), 40, 45, 15);
            }
            else if (orientation == 1)
            {
                if (movingPos == 9)
                    movingPos = 8;
                gc.fillRect((443 - 428) + 50 + ((movingPos) * 15), 25, 15, 45);
                gc.fillRect(50 + ((movingPos) * 15), 40, 30, 15);

            }
            else if (orientation == 0)
            {
                if (movingPos == 8 || movingPos == 9)
                    movingPos = 7;
                gc.fillRect((443 - 428) + 50 + ((movingPos) * 15), 40, 15, 15);
                gc.fillRect(50 + ((movingPos) * 15), 25, 45, 15);
            }
            else if (orientation == 3)
            {
                if (movingPos == 9)
                    movingPos = 8;
                gc.fillRect((443 - 428) + 50 + ((movingPos - 1) * 15), 25, 15, 45);
                gc.fillRect(65 + ((movingPos - 1) * 15), 40, 30, 15);
            }


        }
        if (currentBlock == 2)
        {
            if (movingPos == 9)
                movingPos = 8;
            gc.setFill(yellow);
            gc.fillRect(50 + ((movingPos) * 15), 25, 30, 30);
        }
        if (currentBlock == 3)
        {
            orientation %= 2;
            gc.setFill(cyan);
            if (orientation == 0)
            {
                if (movingPos == 7 || movingPos == 8 || movingPos == 9)
                    movingPos = 6;
                gc.fillRect(50 + ((movingPos) * 15), 25, 60, 15);
            }
            if (orientation == 1)
            {
                gc.fillRect(50 + ((movingPos) * 15), 25, 15, 60);
            }

        }
        if (currentBlock == 4)
        {
            orientation %= 2;
            gc.setFill(red);
            if (orientation == 0)
            {
                if (movingPos == 8 || movingPos == 9)
                    movingPos = 7;
                gc.fillRect(65 + ((movingPos) * 15), 25, 30, 15);
                gc.fillRect(50 + ((movingPos) * 15), 40, 30, 15);
            }
            else
            {
                if (movingPos == 9)
                    movingPos = 8;
                gc.fillRect(65 + ((movingPos) * 15), 40, 15, 30);
                gc.fillRect(50 + ((movingPos) * 15), 25, 15, 30);
            }


        }
        if (currentBlock == 5)
        {
            orientation %= 2;
            gc.setFill(green);
            if (orientation == 0)
            {
                if (movingPos == 8 || movingPos == 9)
                    movingPos = 7;
                gc.fillRect(50 + ((movingPos) * 15), 25, 30, 15);
                gc.fillRect(65 + ((movingPos) * 15), 40, 30, 15);
            }
            else
            {
                if (movingPos == 9)
                    movingPos = 8;
                gc.fillRect(50 + ((movingPos) * 15), 40, 15, 30);
                gc.fillRect(65 + ((movingPos) * 15), 25, 15, 30);
            }


        }
        if (currentBlock == 6)
        {
            gc.setFill(blue);
            if (orientation == 0)
            {
                if (movingPos >= 8)
                    movingPos = 7;
                gc.fillRect(50 + ((movingPos) * 15), 25, 15, 30);
                gc.fillRect(50 + ((movingPos) * 15), 25, 45, 15);
            }
            else if (orientation == 1)
            {
                if (movingPos >= 9)
                    movingPos = 8;
                gc.fillRect(65 + ((movingPos) * 15), 25, 15, 45);
                gc.fillRect(50 + ((movingPos) * 15), 25, 30, 15);
            }
            else if (orientation == 2)
            {
                if (movingPos >= 8)
                    movingPos = 7;
                gc.fillRect(80 + ((movingPos) * 15), 25, 15, 30);
                gc.fillRect(50 + ((movingPos) * 15), 40, 45, 15);
            }
            else if (orientation == 3)
            {
                if (movingPos >= 9)
                    movingPos = 8;
                gc.fillRect(50 + ((movingPos) * 15), 25, 15, 45);
                gc.fillRect(50 + ((movingPos) * 15), 55, 30, 15);
            }
        }
        if (currentBlock == 7)
        {
            gc.setFill(orange);
            if (orientation == 0)
            {
                if (movingPos >= 8)
                    movingPos = 7;
                gc.fillRect(80 + ((movingPos) * 15), 25, 15, 30);
                gc.fillRect(50 + ((movingPos) * 15), 25, 45, 15);
            }
            else if (orientation == 1)
            {
                if (movingPos >= 9)
                    movingPos = 8;
                gc.fillRect(65 + ((movingPos) * 15), 25, 15, 45);
                gc.fillRect(50 + ((movingPos) * 15), 55, 30, 15);
            }
            else if (orientation == 2)
            {
                if (movingPos >= 8)
                    movingPos = 7;
                gc.fillRect(50 + ((movingPos) * 15), 25, 15, 30);
                gc.fillRect(50 + ((movingPos) * 15), 40, 45, 15);
            }
            else if (orientation == 3)
            {
                if (movingPos >= 9)
                    movingPos = 8;
                gc.fillRect(50 + ((movingPos) * 15), 25, 15, 45);
                gc.fillRect(50 + ((movingPos) * 15), 25, 30, 15);
            }
        }

        //draw outline
        gc.setFill(red);
        gc.fillRect(50, 25, 150, 1);
        gc.fillRect(50, 25 + 300, 150, 1);
        gc.fillRect(50, 25, 1, 300);
        gc.fillRect(50 + 150, 25, 1, 300);
    }

    private void clearLines()
    {
        for (int j = 0; j < 20; j++)
        {
            boolean verifier = true;
            for (int i = 0; i < 10; i++)
                if (field[i][j] == 0)
                    verifier = false;

            if (verifier)
            {
                fieldHeight--;
                linesCleared++;
                for (int k = j; k < 19; k++)
                    for (int i = 0; i < 10; i++)
                        field[i][k] = field[i][k + 1];
            }
        }
    }

    private void hang()
    {
        try
        {
            System.in.read();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private static double tanh(double val)
    {
        return (exp(2 * val) - 1) / (exp(2 * val) + 1);
    }

    private static double exp(double val)
    {
        if (val < -700)
            return 0;
        if (val > 700)
            val = 700;
        final long tmp = (long) (1512775 * val + (1072693248 - 60801));
        return Double.longBitsToDouble(tmp << 32);
    }

    private static final double C0 = 1.57073, C1 = -0.212053, C2 = 0.0740935, C3 = -0.0186166;

}