//==========================================================================
//   CMESSAGE.H  -  header for
//                     OMNeT++/OMNEST
//            Discrete System Simulation in C++
//
//  Author: Andras Varga
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2008 Andras Varga
  Copyright (C) 2006-2008 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#ifndef __CMESSAGE_H
#define __CMESSAGE_H

#include "cevent.h"
#include "carray.h"
#include "cmsgpar.h"
#include "csimulation.h"

NAMESPACE_BEGIN

class cMsgPar;
class cGate;
class cChannel;
class cModule;
class cSimpleModule;
class cSimulation;
class cMessageHeap;
class LogBuffer;


/**
 * Predefined message kind values (values for cMessage's getKind(),
 * setKind() methods).
 *
 * Negative values are reserved for the \opp system and its
 * standard libraries. Zero and positive values can be freely used
 * by simulation models.
 */
enum eMessageKind
{
  MK_STARTER = -1,  /// Starter message. Used by scheduleStart().
  MK_TIMEOUT = -2,  /// Internal timeout message. Used by wait(), etc.
  MK_PACKET  = -3,  /// Obsolete
  MK_INFO    = -4,  /// Obsolete

  MK_PARSIM_BEGIN = -1000  /// values -1000...-2000 reserved for parallel simulation
};

/**
 * Maximum number of partitions for parallel simulation.
 *
 * @ingroup ParsimBrief
 * @ingroup Parsim
 */
// Note: it cannot go to cparsimcomm.h, without causing unwanted dependency on sim/parsim
#define MAX_PARSIM_PARTITIONS  32768 // srcprocid in cMessage


/**
 * The message class in \opp. cMessage objects may represent events,
 * messages, jobs or other entities in a simulation. To represent network
 * packets, use the cPacket subclass.
 *
 * Messages may be scheduled (to arrive back at the same module at a later
 * time), cancelled, sent out on a gate, or sent directly to another module;
 * all via methods of cSimpleModule.
 *
 * cMessage can be assigned a name (a property inherited from cNamedObject);
 * other attributes include message kind, priority, and time stamp.
 * Messages may be cloned with the dup() function. The control info field
 * facilitates modelling communication between protocol layers. The context
 * pointer field makes it easier to work with several timers (self-messages)
 * at a time. A message also stores information about its last sending,
 * including sending time, arrival time, arrival module and gate.
 *
 * Useful methods are isSelfMessage(), which tells apart self-messages from
 * messages received from other modules, and isScheduled(), which returns
 * whether a self-message is currently scheduled.
 *
 * Further fields can be added to cMessage via message declaration files (.msg)
 * which are translated into C++ classes. An example message declaration:
 *
 * \code
 * message Job
 * {
 *        string label;
 *        int color = -1;
 * }
 * \endcode
 *
 * @see cSimpleModule, cQueue, cPacket
 *
 * @ingroup SimCore
 */
class SIM_API cMessage : public cEvent
{
    friend class LogBuffer;  // for setMessageId()

  private:
    enum {
        FL_ISPRIVATECOPY = 4,
    };
    // note: fields are in an order that maximizes packing (minimizes sizeof(cMessage))
    short msgkind;             // message kind -- 0>= user-defined meaning, <0 reserved
    short srcprocid;           // reserved for use by parallel execution: id of source partition
    cArray *parlistp;          // ptr to list of parameters
    cObject *ctrlp;            // ptr to "control info"
    void *contextptr;          // a stored pointer -- user-defined meaning, used with self-messages

    int frommod, fromgate;     // source module and gate IDs -- set internally
    int tomod, togate;         // dest. module and gate IDs -- set internally
    simtime_t created;         // creation time -- set be constructor
    simtime_t sent;            // time of sending -- set internally
    simtime_t tstamp;          // time stamp -- user-defined meaning

    long msgid;                // a unique message identifier assigned upon message creation
    long msgtreeid;            // a message identifier that is inherited by dup, if non dupped it is msgid
    static long next_id;       // the next unique message identifier to be assigned upon message creation

    // global variables for statistics
    static long total_msgs;
    static long live_msgs;

  private:
    // internal: create parlist
    void _createparlist();

    void copy(const cMessage& msg);

    // internal: used by LogBuffer for creating an *exact* copy of a message
    void setId(long id) {msgid = id;}

  public:
    // internal: create an exact clone (including msgid) that doesn't show up in the statistics
    cMessage* privateDup() const;

    // internal: called by the simulation kernel as part of the send(),
    // scheduleAt() calls to set the values returned by the
    // getSenderModuleId(), getSenderGate(), getSendingTime() methods.
    void setSentFrom(cModule *module, int gateId, simtime_t_cref t);

    // internal: use the public, documented setArrival(int,int,simtime_t_cref) instead
    _OPPDEPRECATED void setArrival(cModule *module, int gateId, simtime_t_cref t);

    // internal: used by the parallel simulation kernel.
    void setSrcProcId(int procId) {srcprocid = (short)procId;}

    // internal: used by the parallel simulation kernel.
    virtual int getSrcProcId() const {return srcprocid;}

    // internal: returns the parameter list object, or NULL if it hasn't been used yet
    cArray *getParListPtr()  {return parlistp;}

  private: // hide cEvent methods from the cMessage API

    // overridden from cEvent: return true
    virtual bool isMessage() const {return true;}

    // overridden from cEvent: return true of the target module is still alive and well
    virtual bool isStale();

    // overridden from cEvent: return the arrival module
    virtual cObject *getTargetObject() const;

    // overridden from cEvent
    virtual void execute();

  public:
    /** @name Constructors, destructor, assignment */
    //@{

    /**
     * Copy constructor.
     */
    cMessage(const cMessage& msg);

    /**
     * Constructor.
     */
    explicit cMessage(const char *name=NULL, short kind=0);

    /**
     * Destructor.
     */
    virtual ~cMessage();

    /**
     * Assignment operator. The data members NOT copied are: object name
     * (see cNamedObject's operator=() for more details) and message ID.
     * All other members, including creation time and message tree ID,
     * are copied.
     */
    cMessage& operator=(const cMessage& msg);
    //@}

    /**
     * Returns whether the current class is subclass of cPacket.
     * The cMessage implementation returns false.
     */
    virtual bool isPacket() const {return false;}

    /** @name Redefined cObject member functions. */
    //@{

    /**
     * Creates and returns an exact copy of this object, except for the
     * message ID (the clone is assigned a new ID). Note that the message
     * creation time is also copied, so clones of the same message object
     * have the same creation time. See cObject for more details.
     */
    virtual cMessage *dup() const  {return new cMessage(*this);}

    /**
     * Produces a one-line description of the object's contents.
     * See cObject for more details.
     */
    virtual std::string info() const;

    /**
     * Produces a multi-line description of the object's contents.
     * See cObject for more details.
     */
    virtual std::string detailedInfo() const;

    /**
     * Calls v->visit(this) for each contained object.
     * See cObject for more details.
     */
    virtual void forEachChild(cVisitor *v);

    /**
     * Serializes the object into an MPI send buffer
     * Used by the simulation kernel for parallel execution.
     * See cObject for more details.
     */
    virtual void parsimPack(cCommBuffer *buffer) const;

    /**
     * Deserializes the object from an MPI receive buffer
     * Used by the simulation kernel for parallel execution.
     * See cObject for more details.
     */
    virtual void parsimUnpack(cCommBuffer *buffer);
    //@}

    /** @name Message attributes. */
    //@{
    /**
     * Sets the message kind. Nonnegative values can be freely used by
     * the user; negative values are reserved by OMNeT++ for internal
     * purposes.
     */
    void setKind(short k)  {msgkind=k;}

    /**
     * Sets the message's time stamp to the current simulation time.
     */
    void setTimestamp() {tstamp=simulation.getSimTime();}

    /**
     * Directly sets the message's time stamp.
     */
    void setTimestamp(simtime_t t) {tstamp=t;}

    /**
     * Sets the context pointer. This pointer may store an arbitrary value.
     * It is useful when managing several timers (self-messages): when
     * scheduling the message one can set the context pointer to the data
     * structure the timer corresponds to (e.g. the buffer whose timeout
     * the message represents), so that when the self-message arrives it is
     * easier to identify where it belongs.
     */
    void setContextPointer(void *p) {contextptr=p;}

    /**
     * Attaches a "control info" structure (object) to the message.
     * This is most useful when passing packets between protocol layers
     * of a protocol stack: e.g. when sending down an IP datagram to Ethernet,
     * the attached "control info" can contain the destination MAC address.
     *
     * The "control info" object will be deleted when the message is deleted.
     * Only one "control info" structure can be attached (the second
     * setControlInfo() call throws an error).
     *
     * When the message is duplicated or copied, copies will have their
     * control info set to NULL because the cObject interface
     * does not define dup/copy operations.
     * The assignment operator does not change control info.
     */
    void setControlInfo(cObject *p);

    /**
     * Removes the "control info" structure (object) from the message
     * and returns its pointer. Returns NULL if there was no control info
     * in the message.
     */
    cObject *removeControlInfo();

    /**
     * Returns the message kind.
     */
    short getKind() const  {return msgkind;}

    /**
     * Returns the message's time stamp.
     */
    simtime_t_cref getTimestamp() const {return tstamp;}

    /**
     * Returns the context pointer.
     */
    void *getContextPointer() const {return contextptr;}

    /**
     * Returns pointer to the attached "control info".
     */
    cObject *getControlInfo() const {return ctrlp;}
    //@}

    /** @name Dynamically attaching objects. */
    //@{

    /**
     * Returns reference to the 'object list' of the message: a cArray
     * which is used to store parameter (cMsgPar) objects and other objects
     * attached to the message.
     *
     * One can use either getParList() combined with cArray methods,
     * or several convenience methods (addPar(), addObject(), par(), etc.)
     * to add, retrieve or remove cMsgPars and other objects.
     *
     * <i>NOTE: using the object list has alternatives which may better
     * suit your needs. For more information, see class description for discussion
     * about message subclassing vs dynamically attached objects.</i>
     */
    virtual cArray& getParList()  {if (!parlistp) _createparlist(); return *parlistp;}

    /**
     * Add a new, empty parameter (cMsgPar object) with the given name
     * to the message's object list.
     *
     * <i>NOTE: This is a convenience function: one may use getParList() and
     * cArray::add() instead. See also class description for discussion about
     * message subclassing vs dynamically attached objects.</i>
     *
     * @see getParList()
     */
    virtual cMsgPar& addPar(const char *s)  {cMsgPar *p=new cMsgPar(s);getParList().add(p);return *p;}

    /**
     * Add a parameter object to the message's object list.
     *
     * <i>NOTE: This is a convenience function: one may use getParList() and
     * cArray::add() instead. See also class description for discussion about
     * message subclassing vs dynamically attached objects.</i>
     *
     * @see getParList()
     */
    virtual cMsgPar& addPar(cMsgPar *p)  {getParList().add(p); return *p;}

    /**
     * DEPRECATED! Use addPar(cMsgPar *p) instead.
     */
    _OPPDEPRECATED cMsgPar& addPar(cMsgPar& p)  {return addPar(&p);}

    /**
     * Returns the nth object in the message's object list, converting it to a cMsgPar.
     * If the object does not exist or it cannot be cast to cMsgPar (using dynamic_cast\<\>),
     * the method throws a cRuntimeError.
     *
     * <i>NOTE: This is a convenience function: one may use getParList() and
     * cArray::get() instead. See also class description for discussion about
     * message subclassing vs dynamically attached objects.</i>
     *
     * @see getParList()
     */
    virtual cMsgPar& par(int n);

    /**
     * Returns the object with the given name in the message's object list,
     * converting it to a cMsgPar.
     * If the object does not exist or it cannot be cast to cMsgPar (using dynamic_cast\<\>),
     * the method throws a cRuntimeError.
     *
     * <i>NOTE: This is a convenience function: one may use getParList() and
     * cArray::get() instead. See also class description for discussion about
     * message subclassing vs dynamically attached objects.</i>
     *
     * @see getParList()
     */
    virtual cMsgPar& par(const char *s);

    /**
     * Returns the index of the parameter with the given name in the message's
     * object list, or -1 if it could not be found.
     *
     * <i>NOTE: This is a convenience function: one may use getParList() and
     * cArray::find() instead. See also class description for discussion about
     * message subclassing vs dynamically attached objects.</i>
     *
     * @see getParList()
     */
    virtual int findPar(const char *s) const;

    /**
     * Check if a parameter with the given name exists in the message's
     * object list.
     *
     * <i>NOTE: This is a convenience function: one may use getParList() and
     * cArray::exist() instead. See also class description for discussion about
     * message subclassing vs dynamically attached objects.</i>
     *
     * @see getParList()
     */
    virtual bool hasPar(const char *s) const {return findPar(s)>=0;}

    /**
     * Add an object to the message's object list.
     *
     * <i>NOTE: This is a convenience function: one may use getParList() and
     * cArray::add() instead. See also class description for discussion about
     * message subclassing vs dynamically attached objects.</i>
     *
     * @see getParList()
     */
    virtual cObject *addObject(cObject *p)  {getParList().add(p); return p;}

    /**
     * Returns the object with the given name in the message's object list.
     * If the object is not found, it returns NULL.
     *
     * <i>NOTE: This is a convenience function: one may use getParList() and
     * cArray::get() instead. See also class description for discussion about
     * message subclassing vs dynamically attached objects.</i>
     *
     * @see getParList()
     */
    virtual cObject *getObject(const char *s)  {return getParList().get(s);}

    /**
     * Check if an object with the given name exists in the message's object list.
     *
     * <i>NOTE: This is a convenience function: one may use getParList() and
     * cArray::exist() instead. See also class description for discussion about
     * message subclassing vs dynamically attached objects.</i>
     *
     * @see getParList()
     */
    virtual bool hasObject(const char *s)  {return !parlistp ? false : parlistp->find(s)>=0;}

    /**
     * Remove the object with the given name from the message's object list, and
     * return its pointer. If the object does not exist, NULL is returned.
     *
     * <i>NOTE: This is a convenience function: one may use getParList() and
     * cArray::remove() instead. See also class description for discussion about
     * message subclassing vs dynamically attached objects.</i>
     *
     * @see getParList()
     */
    virtual cObject *removeObject(const char *s)  {return getParList().remove(s);}

    /**
     * Remove the object with the given name from the message's object list, and
     * return its pointer. If the object does not exist, NULL is returned.
     *
     * <i>NOTE: This is a convenience function: one may use getParList() and
     * cArray::remove() instead. See also class description for discussion about
     * message subclassing vs dynamically attached objects.</i>
     *
     * @see getParList()
     */
    virtual cObject *removeObject(cObject *p)  {return getParList().remove(p);}
    //@}

    /** @name Sending/arrival information. */
    //@{

    /**
     * Return true if message was posted by scheduleAt().
     */
    bool isSelfMessage() const {return togate==-1;}

    /**
     * Returns a pointer to the sender module. It returns NULL if the message
     * has not been sent/scheduled yet, or if the sender module got deleted
     * in the meantime.
     */
    cModule *getSenderModule() const {return simulation.getModule(frommod);}

    /**
     * Returns pointers to the gate from which the message was sent and
     * on which gate it arrived. A NULL pointer is returned
     * for new (unsent) messages and messages sent via scheduleAt().
     */
    cGate *getSenderGate() const;

    /**
     * Returns a pointer to the arrival module. It returns NULL if the message
     * has not been sent/scheduled yet, or if the module was deleted
     * in the meantime.
     */
    cModule *getArrivalModule() const {return simulation.getModule(tomod);}

    /**
     * Returns pointers to the gate from which the message was sent and
     * on which gate it arrived. A NULL pointer is returned
     * for new (unsent) messages and messages sent via scheduleAt().
     */
    cGate *getArrivalGate() const;

    /**
     * Returns the module ID of the sender module, or -1 if the
     * message has not been sent/scheduled yet.
     *
     * @see cModule::getId(), cSimulation::getModule()
     */
    int getSenderModuleId() const {return frommod;}

    /**
     * Returns the gate ID of the gate in the sender module on which the
     * message was sent, or -1 if the message has not been sent/scheduled yet.
     * Note: this is not the same as the gate's index (cGate::getIndex()).
     *
     * @see cGate::getId(), cModule::gate(int)
     */
    int getSenderGateId() const   {return fromgate;}

    /**
     * Returns the module ID of the receiver module, or -1 if the
     * message has not been sent/scheduled yet.
     *
     * @see cModule::getId(), cSimulation::getModule()
     */
    int getArrivalModuleId() const {return tomod;}

    /**
     * Returns the gate ID of the gate in the receiver module on which the
     * message was received, or -1 if the message has not been sent/scheduled yet.
     * Note: this is not the same as the gate's index (cGate::getIndex()).
     *
     * @see cGate::getId(), cModule::gate(int)
     */
    int getArrivalGateId() const  {return togate;}

    /**
     * Returns time when the message was created; for cloned messages, it
     * returns the creation time of the original message, not the time of the
     * dup() call.
     */
    simtime_t_cref getCreationTime() const {return created;}

    /**
     * Returns time when the message was sent/scheduled or 0 if the message
     * has not been sent yet.
     */
    simtime_t_cref getSendingTime()  const {return sent;}

    /**
     * Returns time when the message arrived (or will arrive if it
     * is currently scheduled or is underway), or 0 if the message
     * has not been sent/scheduled yet.
     *
     * When the message has nonzero length and it travelled though a
     * channel with nonzero data rate, arrival time may represent either
     * the start or the end of the reception, as returned by the
     * isReceptionStart() method. By default it is the end of the reception;
     * it can be changed by calling setDeliverOnReceptionStart(true) on the
     * gate at receiving end of the channel that has the nonzero data rate.
     *
     * @see getDuration()
     */
    // note: overridden to provide more specific documentation
    simtime_t_cref getArrivalTime()  const {return delivd;}

    /**
     * Return true if the message arrived through the given gate.
     */
    bool arrivedOn(int gateId) const {return gateId==togate;}

    /**
     * Return true if the message arrived on the gate given with its name.
     * If it is a vector gate, the method returns true if the message arrived
     * on any gate in the vector.
     */
    bool arrivedOn(const char *gatename) const;

    /**
     * Return true if the message arrived through the given gate
     * in the named gate vector.
     */
    bool arrivedOn(const char *gatename, int gateindex) const;

    /**
     * Returns a unique message identifier assigned upon message creation.
     */
    long getId() const {return msgid;}

    /**
     * Returns an identifier which is shared among a message object and all messages
     * created by copying it (i.e. by dup() or the copy constructor).
     */
    long getTreeId() const {return msgtreeid;}
    //@}

    /** @name Miscellaneous. */
    //@{
    /**
     * Override to define a display string for the message. Display string
     * affects message appearance in Tkenv. This default implementation
     * returns "".
     */
    virtual const char *getDisplayString() const;

    /**
     * For use by custom scheduler classes (see cScheduler): set the arrival
     * module and gate for messages inserted into the FES directly by the
     * scheduler. If you pass gateId=-1, the message will arrive as a
     * self-message.
     */
    void setArrival(int moduleId, int gateId) {tomod = moduleId; togate = gateId;}

    /**
     * Like setArrival(int moduleId, int gateId), but also sets the arrival
     * time for the message.
     */
    void setArrival(int moduleId, int gateId, simtime_t_cref t) {tomod = moduleId; togate = gateId; setArrivalTime(t);}
    //@}

    /** @name Statistics. */
    //@{
    /**
     * Returns the total number of messages created since the last
     * reset (reset is usually called my user interfaces at the beginning
     * of each simulation run). The counter is incremented by cMessage constructor.
     * Counter is <tt>signed</tt> to make it easier to detect if it overflows
     * during very long simulation runs.
     * May be useful for profiling or debugging memory leaks.
     */
    static long getTotalMessageCount() {return total_msgs;}

    /**
     * Returns the number of message objects that currently exist in the
     * program. The counter is incremented by cMessage constructor
     * and decremented by the destructor.
     * May be useful for profiling or debugging memory leaks caused by forgetting
     * to delete messages.
     */
    static long getLiveMessageCount() {return live_msgs;}

    /**
     * Reset counters used by getTotalMessageCount() and getLiveMessageCount().
     */
    static void resetMessageCounters()  {total_msgs=live_msgs=0;}
    //@}
};

NAMESPACE_END

#endif


