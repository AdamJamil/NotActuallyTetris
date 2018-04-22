package com.jamil;

import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;

class Node extends Executable
{
    ArrayList<Edge> outgoingEdges = new ArrayList<>();

    boolean visited; //used in traverse method
    boolean output; //whether or not this is an output node
    boolean input; //whether or not this is an input node
    boolean cleaned; //used in clean method
    boolean reachesOutput; //whether or not this node connects to the output

    double AC1; //activation constant 1; range bounded to [0, 10], used as a multiplicative constant
    double AC2; //activation constant 2; range bounded to [0, 2].

    short activationType; //range 0-7, represents which activation function occurs at this node

    double residue; //sum of all inputs
    double val; //f(residue)

    int name;

    void execute()
    {
        //prevents overflow problems
        if (residue > 2000000000)
            residue = 2000000000;
        if (residue < -2000000000)
            residue = -2000000000;

        switch (activationType) //there are 8 activation functions I picked
        {
            //identity (linear) activation function
            case 0:
                val = AC1 * AC2 * residue;
            break;

            //piecewise linear, referred to as hardtanh
            case 1:
                val = AC1 * FastMath.max(-1, FastMath.min(1, FastMath.abs(AC2) * residue));
            break;

            //step function
            case 2:
                val = AC1 * (1 + FastMath.signum(AC2 * residue)) / 2;
            break;

            //bipolar (step function with -1 instead of 0 as output)
            case 3:
                val = AC1 * FastMath.signum(AC2 * residue);
            break;

            //logistic function
            case 4:
                val = AC1 / (1 + exp(-FastMath.abs(AC2) * residue));
            break;

            //tanh
            case 5:
                val = AC1 * tanh(AC2 * residue);
            break;

            //ReLU/rectifier
            case 6:
                val = AC1 * FastMath.max(0, AC2 * residue);
            break;

            //gaussian
            case 7:
                val = AC1 * exp(-FastMath.abs(AC2) * residue * residue);
            break;
        }
    }

    //constructor used for inputs nodes - linear activation with all constants of 1 is identity
    Node()
    {
        AC2 = 1;
        AC1 = 1;
        activationType = 0;
    }

    //constructor used by NeuralNetwork when instantiating from NodeGene
    Node(int name)
    {
        this();
        this.name = name;
    }

    @Override
    public String toString()
    {
        if (input)
            return Main.inputs[name];
        if (output)
            return Main.outputs[name];
        return Character.toString(NeuralNetwork.alphabet.charAt(name)); //usually few nodes, so this works well
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
}