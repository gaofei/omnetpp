//
// This file is part of an OMNeT++/OMNEST simulation example.
//
// This file is distributed WITHOUT ANY WARRANTY. See the file
// `license' for details on this and other legal matters.
//
//  Author: Gabor.Lencse@hit.bme.hu
//

/*--------------------------------------------------------------*
  Copyright (C) 1996,97 Gabor Lencse,
  Technical University of Budapest, Dept. of Telecommunications,
  Stoczek u.2, H-1111 Budapest, Hungary.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#include <ctype.h>
#include <string.h>
#include <omnetpp.h>

#include "otherdef.h"
#include "fddi_def.h"
#include "othermod.h"

Define_Module( Stat )
Define_Module( FDDI_Sink )
//Define_Module( FDDI_Generator )
Define_Module_Like( FDDI_GeneratorFromTraceFile, FDDI_Generator )
Define_Module_Like( FDDI_GeneratorHistogram2x1D, FDDI_Generator )
Define_Module_Like( FDDI_GeneratorPiSquare2x1D, FDDI_Generator )
//Define_Module_Like( FDDI_GeneratorKSplit2x1D, FDDI_Generator )
//Define_Module_Like( FDDI_GeneratorKSplit2D, FDDI_Generator )
Define_Module( FDDI_Address_Generator )
Define_Module( FDDI_Generator4Ring )
Define_Module( FDDI_Generator4Sniffer )
Define_Module( FDDI_Monitor )
Define_Module( LoadControl )

void mystrlwr(char *s)
  {
  while (*s) {*s=(char)tolower(*s); s++;}
  }

char * RingID2prefix(int RingID)
  {
  switch ( RingID )
    {
    case 0:
      return RING_0_PREFIX;
    case 1:
      return RING_1_PREFIX;
    default:
      return 0;
    }
  }

void FDDI_Generator4Ring::activity ()
  {
  char msgname[64];
  int my_station_id = parentModule()->index(); // RO: use only in a ring made by indexing ...

  int no_msg = par("no_msg");
  int no_comps = par("no_comps");
  simtime_t wait_time = par("wait_time");

  for (int i = 1; i <= no_msg; i++)
    {
    // select destination randomly (but not the local station)
    int dest = intrand( no_comps-1 );
    if ( dest == my_station_id )
      dest++;
    strcpy(msgname,"FDDI_FRAME ");
    sprintf(msgname+strlen(msgname),"%d->%d",my_station_id,dest);
    cMessage *msg = new cMessage(msgname, FDDI_FRAME);
    msg->addPar("dest") = (long) dest;
    msg->addPar("source") = (long) my_station_id;
    msg->addPar("gentime") = (double) simTime();
    msg->setLength(50+intrand(4451-8)+idle_bytes); /* min_samples 50, max_samples 4500 */
    /* +idle_bytes: add the (no of idle symbols)/2   (16/2=8) */
    send(msg, "out"); // pass down the message for Token Ring MAC

    wait (wait_time);
    }
  }

void FDDI_Generator::activity ()
  {
  RingID = (short) (long) parentModule()->parentModule()->par("RingID");
  my_station_id = par("StationID");
  if ( my_station_id < 0 )
    return; // StationID < 0 means that this station should not generate traffic
  LoadMultiplier = (double) parentModule()->parentModule()->par("LoadMultiplier");
  char address[12+1+10]; // 12 bytes+EOS, +10 is just for safety
  strcpy(address,par("address"));
  // filename is derived from station address
  char filepathname[100];
  #ifdef __MSDOS__
    strcpy(filepathname,"load\\");
  #else
    strcpy(filepathname,"load/");
  #endif
  strcat(filepathname,RingID2prefix(RingID));
  strcat(filepathname,address+6);
  strcat(filepathname,FileNameEnding());
  mystrlwr(filepathname); // for DOS compatibility
  f=fopen(filepathname,"r");
  if ( !f )
    {
    ev.printf("FDDI_Generator Warning: Cannot open input file %s\n",filepathname);
    ev.printf("No messages will be generated by %s\n",fullPath().c_str());
    //endSimulation();
    return;
    }
  InitStatistics(); // also schedules generator message(s)
  char msgname[64];
  int pkt_len,dest;
  while ( RetrieveDestLength(receive(),dest,pkt_len) ) // also schedules the next generator message
    {
    strcpy(msgname,"FDDI_FRAME ");
    sprintf(msgname+strlen(msgname),"%d->%d",my_station_id, dest);
    cMessage *msg = new cMessage(msgname, FDDI_FRAME);
    msg->addPar("dest") = (long) dest;
    msg->addPar("source") = (long) my_station_id;
    msg->addPar("gentime") = simTime();
    msg->setLength(pkt_len+idle_bytes); /* add the (no of idle symbols)/2 */
    send(msg, "out"); // pass down the message for Token Ring MAC
    }
  // the following message is appropiate only in the case
  // if load is taken directly from a trace file
  // in all other cases RetrieveDestLength MUST return true
  ev.printf("FDDI_Generator Warning: Trace input file %s exhausted\n",filepathname);
  ev.printf("No more messages will be generated by %s\n",fullPath().c_str());
  //endSimulation();
  }

void FDDI_GeneratorFromTraceFile::InitStatistics()
  {
  line = new char[50];
  cMessage *m = new cMessage("GenPack");

  if ( fgets(line,50,f) )
    {
    double t;
    sscanf(line,"%lf,",&t);
    LoadMultiplier = (double) parentModule()->parentModule()->par("LoadMultiplier");
    t /= LoadMultiplier;
    scheduleAt(t,m);
    }
  }

bool FDDI_GeneratorFromTraceFile::RetrieveDestLength(cMessage *m, int &dest, int &pkt_len)
  {
  sscanf(line,"%*f,%d,%d\n",&pkt_len,&dest);
  if ( fgets(line,50,f) )
    {
    double t;
    sscanf(line,"%lf,",&t);
    LoadMultiplier = (double) parentModule()->parentModule()->par("LoadMultiplier");
    t /= LoadMultiplier;
    scheduleAt(t,m);
    return true;
    }
  else
    return false;
  }

void FDDI_GeneratorHistogram2x1D::InitStatistics()
  {
  char line[101];

  fgets(line,101,f); // comment with the ID and address of a source Station
  while ( !feof(f) )
    {
    int dest;
    if ( !fgets(line,101,f) ) // the ID and address of a destination Station
      break; // !! This is a hack: EOF is detected too late...
    sscanf(line,"%i,",&dest);
    cMessage *m = new cMessage("GenPack"); // a self message, it will contain all info
    m->addPar("dest") = (long)dest; // ID of destination station
    cDoubleHistogram *delay = new cDoubleHistogram("inter-arrivial time");
    delay->loadFromFile(f); // read the distribution of the inter-arrival time
    m->addPar("delay").setDoubleValue(delay);
    cLongHistogram *length = new cLongHistogram("packet length");
    length->loadFromFile(f); // read the distribution of the packet length
    m->addPar("length").setDoubleValue(length);
    LoadMultiplier = (double) parentModule()->parentModule()->par("LoadMultiplier");
    scheduleAt(simTime()+delay->random()/LoadMultiplier,m); // the 1st packet to 'dest'
    // Remark:
    // This generator will generate packets destined to those stations only
    // the ID of that was found in the statistical input file
    }
  }

bool FDDI_GeneratorHistogram2x1D::RetrieveDestLength(cMessage *m, int &dest, int &pkt_len)
  {
  pkt_len = (int) m->par("length");
  dest = (int) m->par("dest");
  LoadMultiplier = (double) parentModule()->parentModule()->par("LoadMultiplier");
// debug begin
  double delay = (double) m->par("delay");
  if ( delay < 0 )
    error("Negative inter-arrival time of value %lf in %s", delay, fullPath().c_str());
  scheduleAt(simTime()+delay/LoadMultiplier,m);
// original code was:
//  scheduleAt(simTime()+(double)m->par("delay")/LoadMultiplier,m);
  return true;
  }

void FDDI_GeneratorPiSquare2x1D::InitStatistics()
  {
  char line[101];

  fgets(line,101,f); // comment with the ID and address of a source Station
  while ( !feof(f) )
    {
    int dest;
    if ( !fgets(line,101,f) ) // the ID and address of a destination Station
      break; // !! This is a hack: EOF is detected too late...
    sscanf(line,"%i,",&dest);
    cMessage *m = new cMessage("GenPack"); // a self message, it will contain all info
    m->addPar("dest") = (long)dest; // ID of destination station
    cPSquare *delay = new cPSquare;
    delay->loadFromFile(f); // read the distribution of the inter-arrivial time
    m->addPar("delay").setDoubleValue(delay);
    cPSquare *length = new cPSquare;
    length->loadFromFile(f); // read the distribution of the packet length
    m->addPar("length").setDoubleValue(length);
    LoadMultiplier = (double) parentModule()->parentModule()->par("LoadMultiplier");
    scheduleAt(simTime()+delay->random()/LoadMultiplier,m); // the 1st packet to 'dest'
    // Remark:
    // This generator will generate packets destined to those stations only
    // the ID of that was found in the statistical input file
    }
  }

bool FDDI_GeneratorPiSquare2x1D::RetrieveDestLength(cMessage *m, int &dest, int &pkt_len)
  {
  pkt_len = (int) m->par("length");
  dest = (int) m->par("dest");
  LoadMultiplier = (double) parentModule()->parentModule()->par("LoadMultiplier");
  scheduleAt(simTime()+(double)m->par("delay")/LoadMultiplier,m);
  return true;
  }

void FDDI_Address_Generator::activity ()
  {
  RingID = (short) (long) parentModule()->parentModule()->par("RingID");
  my_station_id = par("StationID");
  if ( my_station_id < 0 )
    return; // StationID < 0 means that this station should not generate traffic
  //LoadMultiplier = (double) parentModule()->parentModule()->par("LoadMultiplier");
  char address[12+1+10]; // 12 bytes+EOS, +10 is just for safety
  strcpy(address,par("address"));
  // filename is derived from station address
  char filepathname[100];
  #ifdef __MSDOS__
    strcpy(filepathname,"load\\");
  #else
    strcpy(filepathname,"load/");
  #endif
  strcat(filepathname,RingID2prefix(RingID));
  strcat(filepathname,address+6);
  strcat(filepathname,FileNameEnding());
  mystrlwr(filepathname); // for DOS compatibility
  f=fopen(filepathname,"r");
  if ( !f )
    {
    ev.printf("FDDI_Address_Generator Warning: Cannot open input file %s\n",filepathname);
    ev.printf("No messages will be routed by %s\n",fullPath().c_str());
    //endSimulation();
    return;
    }
  InitStatistics(); // just reads the histograms, makes no schedule
  char msgname[64];
  int pkt_len,dest;
  cMessage * m;
  for ( ; ; )
    {
    m = receive();
    pkt_len=m->length()-idle_bytes;
    dest = RetrieveNewAddress(pkt_len);
    strcpy(msgname,"FDDI_FRAME ");
    sprintf(msgname+strlen(msgname),"%d->%d",my_station_id, dest);
    cMessage *msg = new cMessage(msgname, FDDI_FRAME);
    msg->addPar("dest") = (long) dest;
    msg->addPar("source") = (long) my_station_id;
    msg->addPar("gentime") = simTime();
    msg->setLength(pkt_len+idle_bytes); /* add the (no of idle symbols)/2 */
    send(msg, "out"); // pass down the message for Token Ring MAC
    delete m;
    }
  }

void FDDI_Address_Generator::InitStatistics()
  {
  char line[101];

  fgets(line,101,f); // comment with the ID and address of a source Station
  while ( !feof(f) )
    {
    histogram_plus_address * hist_plus_addr = new histogram_plus_address;
    if ( !fgets(line,101,f) ) // the ID and address of a destination Station
      break; // !! This is a hack: EOF is detected too late...
    sscanf(line,"%i,",&hist_plus_addr->dest); // ID of destination station
    cDoubleHistogram *delay = new cDoubleHistogram;
    delay->loadFromFile(f); // read the distribution of the inter-arrivial time
    delete delay; // we do not use it!
    hist_plus_addr->length.loadFromFile(f); // read the distribution of the packet length
    length_histograms.add(&hist_plus_addr);
    // Remark:
    // This address generator will route packets to those stations only
    // the ID of that was found in the statistical input file
    }
  }

int FDDI_Address_Generator::RetrieveNewAddress(int pkt_len)
  {
  int i;
  double sum, random;
  cLongHistogram * hist;

  for ( i=0, sum=0; i < length_histograms.items(); i++)
    {
    hist = &((*( (histogram_plus_address **)(length_histograms[i]) ))->length);
    sum += hist->pdf(pkt_len)*hist->samples();
    }
  random = dblrand()*sum;
  for ( i=0, sum=0; sum<random; i++)
    {
    hist = &((*( (histogram_plus_address **)(length_histograms[i]) ))->length);
    sum += hist->pdf(pkt_len)*hist->samples();
    }
  return i-1;
  }

void FDDI_Generator4Sniffer::activity()
  {
  int my_station_id = par("StationID");
  char address[12+1+10]; // 12 bytes, +10 is just for safety
  strcpy(address,par("address"));

  if ( my_station_id < 0 )
    return; // StationID < 0 means that this station should not generate traffic

  // the following lines may be used to generate traffic e.g. for measurement purposes
  // cMessage *m = new cMessage("GenPack");
  //
  // while ( 1 )
  //   {
  //   scheduleAt(simTime()+uniform(0,100e-6),m);
  //   m=receive();
  //
  //   strcpy(msgname,"FDDI_FRAME from Sniffer to self");
  //   //sprintf(msgname+strlen(msgname),"%d->%d",my_station_id,dest);
  //   cMessage *msg = new cMessage(msgname, FDDI_FRAME);
  //   msg->addPar("dest") = (long) my_station_id;
  //   msg->addPar("source") = (long) my_station_id;
  //   msg->addPar("gentime") = simTime();
  //   msg->setLength(12); // just to be a short frame not to make much load
  //   // this length is inpossible in a real FDDI network!
  //   send(msg, "out");
  //   }
  }

void Stat::activity()
  {
  for(;;)
    {
    cMessage *msg = receive();
    delete msg;
    }
  }

void FDDI_Sink::activity()
  {
//  cOutVector queuing_time("queuing-time",1);
//  cOutVector transm_time("transmission-time",1);
  for(;;)
    {
    cMessage *msg = receive();
    // extract statistics and write out to vector file
//    simtime_t generated = msg->par("gentime"),
//	      sent	= msg->par("sendtime"),
//	      arrived	= msg->arrivalTime();
//    queuing_time.record( sent - generated );
//    transm_time.record( arrived - sent );
//    ev.printf("Time between creation and arrival: %lf\n", arrived-generated);
    delete msg;
    }
  }

void FDDI_Monitor::activity()
  {
  char outvectorname[100];
  sprintf(outvectorname,"packet-length in ring #%i",
    (short) (long) parentModule()->parentModule()->par("RingID"));
  cOutVector packet_length(outvectorname,1);
//  cOutVector queuing_time("queuing-time",1);
//  cOutVector transm_time("transmission-time",1);
  cBag queueing_time;
  for(;;)
    {
    cMessage *msg = receive();
    int length = msg->length()-idle_bytes;
/*  queueing time recording was commented out to fasten postprocessing
    simtime_t generated = msg->par("gentime"),
	      sent	= msg->par("sendtime"),
	      arrived	= msg->arrivalTime();
    int source = (long) msg->par("source");
    if ( queueing_time.isUsed(source) )
      outvect = * ((cOutVector **) (queueing_time[source]) );
    else
      {
      sprintf(outvectorname,"Queueing time in ring #%i, from station #%i",
	(short) (long) parentModule()->parentModule()->par("RingID"),source);
      outvect = new cOutVector(outvectorname,1);
      queueing_time.addAt(source,&outvect);
      }
    outvect->record( sent - generated );
*/
//    transm_time.record( arrived - sent );
//    ev.printf("Time between creation and arrival: %lf\n", arrived-generated);
    packet_length.record(length);
    delete msg;
    }
  }

void LoadControl::activity()
  {
  char filepathname[100];
  strcpy(filepathname,par("LoadControlFile"));
  FILE *f=fopen(filepathname,"r");
  if ( !f )
    {
    ev.printf("Load Control Warning: Cannot open input file %s\n",filepathname);
    ev.printf("No Load Control will be done by %s\n",fullPath().c_str());
    //endSimulation();
    return;
    }
  char line[101];
  fgets(line,101,f); // comment with the ID and namestr of the ring
  while ( !feof(f) )
    {
    double t, LoadMultiplier;
    if ( !fgets(line,101,f) ) // time and LoadMultiplier
      break; // !! This is a hack: EOF is detected too late...
    sscanf(line,"%lf %lf",&t,&LoadMultiplier);
    cMessage *m = new cMessage("LoadControlSelfMsg",LOAD_CONTROL_SELFMSG);
    scheduleAt(t,m);
    m = receive();
    delete m;
    parentModule()->par("LoadMultiplier")=LoadMultiplier;
    }
  }
