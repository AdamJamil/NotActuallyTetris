package com.jamil;

class Edge extends Executable
{
    Node incomingNode;
    Node outgoingNode;
    double weight;

    int name;

    //sends value to next node
    public void execute()
    {
        outgoingNode.residue += incomingNode.val * weight;
    }

    Edge()
    {

    }

    @Override
    public String toString()
    {
        return Integer.toString(name);
    }
}
