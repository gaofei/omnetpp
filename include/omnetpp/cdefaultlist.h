//==========================================================================
//  CDEFAULTLIST.H - part of
//
//                     OMNeT++/OMNEST
//            Discrete System Simulation in C++
//
//
//  Declaration of the following classes:
//    cDefaultList : holds a set of cOwnedObjects
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2008 Andras Varga
  Copyright (C) 2006-2008 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#ifndef __CDEFAULTLIST_H
#define __CDEFAULTLIST_H

#include "cownedobject.h"

NAMESPACE_BEGIN


/**
 * Internal class, used as a base class for modules and channels. It is not
 * intended for subclassing outside the simulation kernel.
 *
 * cDefaultList acts as a "soft owner" (see object ownership discussion
 * in cOwnedObject documentation).
 *
 * @ingroup Internals
 */
class SIM_API cDefaultList : public cNoncopyableOwnedObject
{
    friend class cObject;
    friend class cOwnedObject;
    friend class cChannelType;

  private:
    enum {FL_PERFORMFINALGC = 2};  // whether to delete owned objects in the destructor

  private:
    cOwnedObject **vect; // vector of objects
    int capacity;        // allocated size of vect[]
    int size;            // number of elements used in vect[] (0..size-1)

#ifdef SIMFRONTEND_SUPPORT
  private:
    int64_t lastChangeSerial;
#endif

  private:
    void construct();
    void doInsert(cOwnedObject *obj);
    virtual void ownedObjectDeleted(cOwnedObject *obj);
    virtual void yieldOwnership(cOwnedObject *obj, cObject *newOwner);

  public:
    // internal: called from module creation code in ctypes.cc
    void takeAllObjectsFrom(cDefaultList& other);

#ifdef SIMFRONTEND_SUPPORT
    // internal: used by the UI to optimize refreshes
    void updateLastChangeSerial()  {lastChangeSerial = changeCounter++;}
    virtual bool hasChangedSince(int64_t lastRefreshSerial);
#endif

  protected:
    /** @name Redefined cObject member functions */
    //@{

    /**
     * Redefined.
     */
    void take(cOwnedObject *obj);

    /**
     * Redefined.
     */
    void drop(cOwnedObject *obj);

  public:
    /** @name Constructors, destructor, assignment. */
    //@{
    /**
     * Constructor.
     */
    explicit cDefaultList(const char *name=NULL);

    /**
     * Destructor. The contained objects will be deleted.
     */
    virtual ~cDefaultList();
    //@}

    /** @name Redefined cObject methods. */
    //@{
    /**
     * Returns true.
     */
    virtual bool isSoftOwner() const {return true;}

    /**
     * Produces a one-line description of the object's contents.
     * See cObject for more details.
     */
    virtual std::string info() const;

    /**
     * Calls v->visit(this) for each contained object.
     * See cObject for more details.
     */
    virtual void forEachChild(cVisitor *v);

    /**
     * Packing and unpacking cannot be supported with this class.
     * This method raises an error.
     */
    virtual void parsimPack(cCommBuffer *buffer) const;

    /**
     * Packing and unpacking cannot be supported with this class.
     * This method raises an error.
     */
    virtual void parsimUnpack(cCommBuffer *buffer);
    //@}

    /** @name Container functions. */
    // Note: we need long method names here because cModule subclasses from this class
    //@{

    /**
     * Whether the destructor will delete garbage objects (owned objects
     * that have not been deallocated by destructors of derived classes);
     * see setPerformFinalGC() for details. The default setting is false.
     */
    bool getPerformFinalGC() const  {return flags&FL_PERFORMFINALGC;}

    /**
     * Enables deleting of garbage objects in the destructor. Garbage objects
     * are objects that are on this default list at destruct time (see
     * defaultListSize() and defaultListGet()); they are practically objects
     * owned by this module or channel that have not been deallocated
     * by destructors of derived classes.
     *
     * This feature is turned off by default, because it cannot be implemented
     * to work correctly in all cases. The garbage collection routine invokes
     * the <tt>delete</tt> operator on all objects on the default list. This,
     * however, causes problems (crash) in some cases: when the object has
     * been allocated as an element in an array (e.g. <tt>new cQueue[10]</tt>),
     * when the object is part of another object (e.g. <tt>struct X {cQueue q;};</tt>),
     * or any other case when the object's pointer is not deleteable.
     * Note that the presence of such data structures in a module
     * does not automatically rule out the use of the final GC mechanism.
     * Final GC can still be turned on if care is taken that the module's
     * destructor deallocates those arrays and structs/objects, so that
     * the contained objects disappear before the GC process could see them.
     * Conclusion: only turn on final GC if you know what you are doing.
     */
    virtual void setPerformFinalGC(bool b)  {setFlag(FL_PERFORMFINALGC,b);}

    /**
     * Returns the number of elements stored.
     */
    int defaultListSize() const {return size;}

    /**
     * Get the element at the given position. k must be between 0 and
     * defaultListSize()-1 (inclusive). If the index is out of bounds,
     * NULL is returned.
     */
    cOwnedObject *defaultListGet(int k);

    /**
     * Returns true if the set contains the given object, false otherwise.
     */
    // Note: we need a long name here because cModule subclasses from this
    bool defaultListContains(cOwnedObject *obj) const;
    //@}
};

NAMESPACE_END


#endif

