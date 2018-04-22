package com.jamil;

import it.unimi.dsi.util.XoRoShiRo128PlusRandom;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

class NeuralNetwork implements Comparable
{
    //mutation probabilities, which are all hyperparameters
    private static final double mutationMultiplier = 1;
    private static final double addNode = 0.2 * mutationMultiplier;
    private static final double addEdge = 0.3 * mutationMultiplier;
    private static final double increaseSize = 0.20 * mutationMultiplier;
    private static final double mutateEdge = 0.35 * mutationMultiplier;
    private static final double mutateNode = 0.35 * mutationMultiplier;

    //used for toString of nodes, but only when requested
    static final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private int nodeCounter = 0; //used for naming nodes
    private int edgeCounter = 0; //used for naming edges

    double fitness; //number of frames survived for

    boolean dead; //if something goes wrong during initialization, the NN is dead
    private boolean hitOutput; //keeps track if an output has been hit during traveral. if not, then NN is killed

    private int inputs, outputs;

    private ArrayList<Node> nodes = new ArrayList<>(); //stores every node from NodeGenes
    private Node[] boxes; //nodes fit inside this array, and edges connect based on the indices of this array. sometimes
    //edges will not connect to anything, if the index does not have a node filled in that location

    private NodeGene[] nodeGenes;
    private EdgeGene[] edgeGenes;
    private short size; //size of boxes array. it should be small at first to ensure that at least some edges connect to something

    private ArrayList<Executable> orderedExecutables = new ArrayList<>(); //contains nodes and edges, in the order that they should be executed in
    private ArrayList<Edge> recurrentEdges = new ArrayList<>(); //contains all the edges that will link to the next execute call

    private ArrayList<Node> stack = new ArrayList<>(); //a temporary arraylist used in the traverse method

    private Node[] inLayer; //input nodes
    private Node[] outLayer; //output nodes

    private static XoRoShiRo128PlusRandom uniformRandom = new XoRoShiRo128PlusRandom(); //faster than Math.random()

    double[] execute(double... input)
    {
        //loads input values into the corresponding nodes
        for (int i = 0; i < inputs; i++)
        {
            if (boxes[i] == null)
            {
                System.out.println("pls halp");
            }
            boxes[i].val = input[i];
        }

        //executes all nodes and edges in order specified by traverse method
        for (Executable object : orderedExecutables)
            object.execute();

        //load output into double array
        double[] result = new double[outputs];
        for (int i = 0; i < outputs; i++)
            result[i] = boxes[size - i - 1].residue;

        //reset the residue so that it does not affect the next iteration
        for (Node node : nodes)
            node.residue = 0;

        //executes the recurrent edges, pushing the old values into new residues
        for (Edge recurrentEdge : recurrentEdges)
            recurrentEdge.execute();

        return result;
    }

    NeuralNetwork(int inputs, int outputs, NodeGene[] inputNodeGenes, EdgeGene[] inputEdgeGenes, short inputSize)
    {
        nodeGenes = inputNodeGenes;
        edgeGenes = inputEdgeGenes;
        size = inputSize;
        this.outputs = outputs;
        this.inputs = inputs;

        boxes = new Node[size];

        inLayer = new Node[inputs];
        outLayer = new Node[outputs];

        //this constructor uses a number to keep track of which input this node is, which is used in the toString method
        for (int i = 0; i < inputs; i++)
            inLayer[i] = new Node(i);

        for (int i = 0; i < outputs; i++)
            outLayer[i] = new Node(i);

        //load all input and output nodes into arrayList
        Collections.addAll(nodes, inLayer);
        Collections.addAll(nodes, outLayer);

        //checks if there is enough room for all the inputs- if not, then kills the neural network
        if (size < inputs)
        {
            dead = true;
            return;
        }
        else for (int i = 0; i < inLayer.length; i++)
        {
            //loads inputs nodes into boxes
            boxes[i] = inLayer[i];
            boxes[i].input = true;
        }

        for (int i = size - 1; i >= size - outputs; i--)
        {
            //checks if there is already a node in this spot. since this would conflict with the output, we kill the NN
            if (boxes[i] != null)
            {
                dead = true;
                return;
            }
            boxes[i] = outLayer[size - 1 - i];
            boxes[i].output = true;
        }

        //load all expressed nodes into arrayList and boxes
        for (NodeGene inputNodeGene : inputNodeGenes)
        {
            //if this node doesn't fit into the boxes, we throw it out
            if (boxes.length <= inputNodeGene.position)
                continue;
            boxes[inputNodeGene.position] = inputNodeGene.node;
            nodes.add(inputNodeGene.node);
        }

        //if an edge satisfies all these requirements, then we add it
        for (EdgeGene edgeGene : inputEdgeGenes)
            if (edgeGene.outgoingNode < size && edgeGene.incomingNode < size
                && boxes[edgeGene.incomingNode] != null && boxes[edgeGene.outgoingNode] != null
                && !boxes[edgeGene.incomingNode].output && !boxes[edgeGene.outgoingNode].input
                && edgeGene.incomingNode != edgeGene.outgoingNode)
                {
                    edgeGene.edge.incomingNode = boxes[edgeGene.incomingNode];
                    edgeGene.edge.outgoingNode = boxes[edgeGene.outgoingNode];
                    edgeGene.edge.incomingNode.outgoingEdges.add(edgeGene.edge); //we only store outgoing edges
                }

        //the traverse method builds the graph representation of the network such that a correct topological sorting
        //can be created. furthermore, we remove edges that cause the graph to be cyclic and store them as recurrent
        //edges. as a result, we are left with an arraylist that gives one possible ordering of execution
        for (Node inputNode : inLayer)
            for (Edge inputEdge : inputNode.outgoingEdges)
            {
                stack.add(inputNode);
                traverse(inputEdge);
                stack.remove(inputNode);
            }

        //some networks have no output at all, which wastes time during testing. this discards such networks
        if (!hitOutput)
        {
            dead = true;
            return;
        }

        //we do not want to execute any input or output nodes
        orderedExecutables.removeAll(Arrays.asList(inLayer));
        orderedExecutables.removeAll(Arrays.asList(outLayer));
    }

    private boolean traverse(Edge edge)
    {
        //we name the edges in the order that they are reached in
        edge.name = edgeCounter;
        edgeCounter++;

        //if we have reached the end, this network does have a path to give some output
        if (edge.outgoingNode.output)
        {
            hitOutput = true;
            edge.incomingNode.reachesOutput = true;
        }

        //if the next node is in the current path, then we are in a cycle, so we mark this edge as recurrent
        if (stack.contains(edge.outgoingNode))
        {
            recurrentEdges.add(edge);
            //the return boolean means that this edge is significant to the output. while it is possible that this
            //recurrent edge does not actually affect the output, it is somewhat difficult to check this, so we assume
            //that it does to save some computational time
            return true;
        }

        //if the next node has been reached, but is not part of the current path, then this is a cross edge
        if (edge.outgoingNode.visited)
        {
            orderedExecutables.add(0, edge);
            return true;
        }

        //at this point, the node has not been visited before at all. so if it is not an output node, we name it
        if (!edge.outgoingNode.output)
        {
            edge.outgoingNode.name = nodeCounter;
            nodeCounter++;
        }

        //mark the node as visited at some point
        edge.outgoingNode.visited = true;

        //while traversing from this node onwards, we leave this node in the stack to show that it's in the current path
        stack.add(edge.outgoingNode);

        //if any of the edges from this node hit the output, that implies this node also hits the output
        for (Edge edge2 : edge.outgoingNode.outgoingEdges)
            edge.outgoingNode.reachesOutput |= traverse(edge2);

        //if this node does hit the output, we want to execute this node and edge
        if (edge.outgoingNode.reachesOutput)
        {
            //this node may have been visited before by a different edge, so we put it before both edges
            //we add it to the beginning to that it comes before everything else in traverse(edge2)
            orderedExecutables.remove(edge.outgoingNode);
            orderedExecutables.add(0, edge.outgoingNode);

            //the edge cannot have been added before, because its incoming node can only be visited once
            orderedExecutables.add(0, edge);
        }

        //this node is no longer in the path once this stack of the traverse method returns
        stack.remove(edge.outgoingNode);

        return edge.outgoingNode.reachesOutput;
    }

    NeuralNetwork mutate()
    {
        int numberOfNodeGenes = nodeGenes.length;
        int numberOfEdgeGenes = edgeGenes.length;
        int mutatedSize = size;

        //possible optimization using exponential distribution
        while (uniformRandom.nextDoubleFast() < addNode)
            numberOfNodeGenes++;
        while (uniformRandom.nextDoubleFast() < addEdge)
            numberOfEdgeGenes++;

        if (uniformRandom.nextDoubleFast() < increaseSize)
            mutatedSize = (int) (10 * uniformRandom.nextDoubleFast()) + size;

        NodeGene[] mutatedNodeGenes = new NodeGene[numberOfNodeGenes];
        EdgeGene[] mutatedEdgeGenes = new EdgeGene[numberOfEdgeGenes];

        //for all the nodeGenes that are being passed down, we either mutate or clone it
        for (int i = 0; i < nodeGenes.length; i++)
            if (uniformRandom.nextDoubleFast() < mutateNode)
                mutatedNodeGenes[i] = nodeGenes[i].mutate();
            else
                mutatedNodeGenes[i] = nodeGenes[i].klone();

        //for all extra nodeGenes (because we mutated the number), create random ones
        for (int i = nodeGenes.length; i < numberOfNodeGenes; i++)
            mutatedNodeGenes[i] = NodeGene.randomNodeGene(inputs, outputs, mutatedSize);

        for (int i = 0; i < edgeGenes.length; i++)
            if (uniformRandom.nextDoubleFast() < mutateEdge)
                mutatedEdgeGenes[i] = edgeGenes[i].mutate();
            else
                mutatedEdgeGenes[i] = edgeGenes[i].klone();

        for (int i = edgeGenes.length; i < numberOfEdgeGenes; i++)
            mutatedEdgeGenes[i] = EdgeGene.randomEdgeGene(mutatedSize);

        return new NeuralNetwork(inputs, outputs, mutatedNodeGenes, mutatedEdgeGenes, (short) mutatedSize);
    }

    //traversal method that resets all values
    void clean()
    {
        for (Node inputNode : inLayer)
            for (Edge outgoingEdge : inputNode.outgoingEdges)
                clean(outgoingEdge.outgoingNode);
    }

    private void clean(Node temp)
    {
        if (temp.cleaned)
            return;
        temp.cleaned = true;
        for (Edge outgoingEdge : temp.outgoingEdges)
            clean(outgoingEdge.outgoingNode);
        temp.residue = 0;
        temp.val = 0;
    }

    @Override
    public String toString()
    {
        if (orderedExecutables.isEmpty())
            return "";

        String newline = System.getProperty("line.separator");
        String output = "{" + newline;
        for (Executable temp : orderedExecutables)
            if (temp instanceof Edge)
                output += "\t" + temp + ": (" + Math.floor(((Edge) temp).weight * 100) / 100 + ((Edge) temp).incomingNode + ") → " + ((Edge) temp).outgoingNode + newline;
            else if (temp instanceof Node)
            {
                output += "\t" + temp + ": f(x) = ";
                Node tempNode = (Node) temp;
                double tempDouble = FastMath.floor(tempNode.AC1 * 100) / 100;
                double tempDouble2 = FastMath.floor(tempNode.AC2 * 100) / 100;
                switch (tempNode.activationType % 8)
                {
                    case 0:
                        output += FastMath.floor(tempNode.AC1 * tempNode.AC2 * 100) / 100 + "x" + newline;
                        break;

                    case 1:
                        output += tempDouble + "max{-1, min{1, " + tempDouble2 + "x}}" + newline;
                        break;

                    case 2:
                        output += tempDouble + " * (1 " + ((FastMath.signum(tempNode.AC2) == -1) ? "- sgn(x)" : "+ sgn(x)") + ") / 2" + newline;
                        break;

                    case 3:
                        output += tempDouble  + " * " + ((FastMath.signum(tempNode.AC2) == -1) ? "-sgn(x)" : "sgn(x)") + newline;
                        break;

                    case 4:
                        output += tempDouble + " / (1 + exp(" + (-FastMath.abs(tempDouble2)) + "x))" + newline;
                        break;

                    case 5:
                        output += tempDouble + "tanh(" + tempDouble2 + "x)" + newline;
                        break;

                    case 6:
                        output += tempDouble + "max{0, " + tempDouble2 + " * x}" + newline;
                        break;

                    case 7:
                        output += tempDouble + "exp(" + (-tempDouble2) + "x^2)" + newline;
                        break;
                }
            }
        output += "}";
        return output;
    }

    @Override
    public int compareTo(Object o)
    {
        return -(int)(fitness - ((NeuralNetwork) o).fitness);
    }
}