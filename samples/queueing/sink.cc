//
// This file is part of an OMNeT++/OMNEST simulation example.
//
// Copyright (C) 1992-2004 Andras Varga
//
// This file is distributed WITHOUT ANY WARRANTY. See the file
// `license' for details on this and other legal matters.
//

#include <omnetpp.h>


class QSink : public cSimpleModule
{
  public:
    Module_Class_Members(QSink,cSimpleModule,0)

    cStdDev qstats;
    cOutVector qtime;

    virtual void initialize();
    virtual void handleMessage(cMessage *msg);
    virtual void finish();
};

Define_Module( QSink );

void QSink::initialize()
{
    qstats.setName("lifetime stats");
    qtime.setName("lifetime vector");
}

void QSink::handleMessage(cMessage *msg)
{
    double d = simTime()-msg->creationTime();
    ev << "Received " << msg->name() << ", lifetime: " << d << "sec" << endl;
    qstats.collect( d );
    qtime.record( d );
    delete msg;
}

void QSink::finish()
{
    ev << "Total jobs processed: " << qstats.samples() << endl;
    ev << "Avg lifetime:         " << qstats.mean() << endl;
    ev << "Max lifetime:         " << qstats.max() << endl;
    ev << "Standard deviation:   " << qstats.stddev() << endl;
}
