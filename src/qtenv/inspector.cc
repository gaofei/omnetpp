//==========================================================================
//  INSPECTOR.CC - part of
//
//                     OMNeT++/OMNEST
//            Discrete System Simulation in C++
//
//  Implementation of
//    inspectors
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2015 Andras Varga
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#include "mainwindow.h"
#include <cstring>
#include <cmath>
#include <cassert>
#include <algorithm>
#include <QMenu>
#include <QContextMenuEvent>
#include <QBoxLayout>
#include <QToolBar>
#include "common/stringutil.h"
#include "omnetpp/cobject.h"
#include "qtenv.h"
#include "tklib.h"
#include "inspector.h"
#include "inspectorfactory.h"
#include "inspectorutil.h"

#define emit

using namespace OPP::common;

namespace omnetpp {
namespace qtenv {

//TODO these two functions are likely not needed any more
const char *insptypeNameFromCode(int code)
{
#define CASE(x)  case x: return #x;
    switch (code) {
        CASE(INSP_DEFAULT);
        CASE(INSP_OBJECT);
        CASE(INSP_GRAPHICAL);
        CASE(INSP_MODULEOUTPUT);
        CASE(INSP_OBJECTTREE);
        default: return "?";
    }
#undef CASE
}

int insptypeCodeFromName(const char *name)
{
#define CASE(x)  if (strcmp(name, #x)==0) return x;
    CASE(INSP_DEFAULT);
    CASE(INSP_OBJECT);
    CASE(INSP_GRAPHICAL);
    CASE(INSP_MODULEOUTPUT);
    CASE(INSP_OBJECTTREE);
    return -1;
#undef CASE
}

// ----

Inspector::Inspector(QWidget *parent, bool isTopLevel, InspectorFactory *f)
    : QWidget(parent, isTopLevel ? Qt::Tool : Qt::Widget)
{
    factory = f;
    //TCLKILL interp = getQtenv()->getInterp();
    object = nullptr;
    type = f->getInspectorType();
    isToplevelWindow = isTopLevel;

    if (isTopLevel) {
        show();
    } else {
        auto layout = new QGridLayout(parent);
        parent->setLayout(layout);
        layout->setMargin(0);
        layout->addWidget(this, 0, 0, 1, 1);
    }
}

Inspector::~Inspector()
{
}

const char *Inspector::getClassName() const
{
    return opp_typename(typeid(*this));
}

bool Inspector::supportsObject(cObject *object) const
{
    return factory->supportsObject(object);
}

std::string Inspector::makeWindowName()
{
    static int counter = 0;
    return opp_stringf(".inspector%d", counter++);
}

void Inspector::doSetObject(cObject *obj)
{
    if (obj != object) {
        if (obj && !supportsObject(obj))
            throw cRuntimeError("Inspector %s doesn't support objects of class %s", getClassName(), obj->getClassName());
        object = obj;
        if(findObjects)
            findObjects->setData(QVariant::fromValue(object));
        // note that doSetObject() is always followed by refresh(), see setObject()
        emit inspectedObjectChanged(object);
    }
}

void Inspector::showWindow()
{
    if (isToplevelWindow) {
        show();
        raise();
    }
}

void Inspector::refresh()
{
    if (isToplevelWindow)
        refreshTitle();

    if(goBackAction)
        goBackAction->setEnabled(canGoBack());
    if(goForwardAction)
        goForwardAction->setEnabled(canGoForward());
    if(object && goUpAction)
    {
        cObject *parent = dynamic_cast<cComponent *>(object) ? ((cComponent *)object)->getParentModule() : object->getOwner();
        goUpAction->setEnabled(parent);
    }
}

void Inspector::refreshTitle()
{
    // update window title (only if changed -- always updating the title produces
    // unnecessary redraws under some window managers
    char newTitle[256];
    const char *prefix = getQtenv()->getWindowTitlePrefix();
    if (!object) {
        sprintf(newTitle, "%s N/A", prefix);
    }
    else {
        std::string fullPath = object->getFullPath();
        if (fullPath.length() <= 60)
            sprintf(newTitle, "%s(%.40s) %s", prefix, getObjectShortTypeName(object), fullPath.c_str());
        else
            sprintf(newTitle, "%s(%.40s) ...%s", prefix, getObjectShortTypeName(object), fullPath.c_str()+fullPath.length()-55);
    }
    if (windowTitle != newTitle) {
        windowTitle = newTitle;
        setWindowTitle(windowTitle.c_str());
    }
}

void Inspector::objectDeleted(cObject *obj)
{
    if (obj == object) {
        doSetObject(nullptr);
        refresh();
    }
    removeFromToHistory(obj);
}

void Inspector::setObject(cObject *obj)
{
    if (obj != object) {
        if (object != nullptr) {
            historyBack.push_back(object);
            historyForward.clear();
        }
        doSetObject(obj);
        refresh();
    }
}

template<typename T>
void removeFromVector(std::vector<T>& vec, T value)
{
    vec.erase(std::remove(vec.begin(), vec.end(), value), vec.end());
}

void Inspector::removeFromToHistory(cObject *obj)
{
    removeFromVector(historyBack, obj);
    removeFromVector(historyForward, obj);
}

bool Inspector::canGoForward()
{
    return !historyForward.empty();
}

bool Inspector::canGoBack()
{
    return !historyBack.empty();
}

void Inspector::goForward()
{
    if (!historyForward.empty()) {
        cObject *newObj = historyForward.back();
        historyForward.pop_back();
        if (object != nullptr)
            historyBack.push_back(object);
        doSetObject(newObj);
        refresh();
    }
}

void Inspector::goBack()
{
    if (!historyBack.empty()) {
        cObject *newObj = historyBack.back();
        historyBack.pop_back();
        if (object != nullptr)
            historyForward.push_back(object);
        doSetObject(newObj);
        refresh();
    }
}

void Inspector::inspectParent()
{
    cObject *parentPtr = dynamic_cast<cComponent *>(object) ? ((cComponent *)object)->getParentModule() : object->getOwner();
    if(parentPtr == nullptr)
        return;

    //inspect in current inspector if possible (and allowed), otherwise open a new one
    if(supportsObject(parentPtr)) { //TODO && $config(reuse-inspectors)
        setObject(parentPtr);
    }
    else
        getQtenv()->inspect(parentPtr);
}
/*TCLKILL
int Inspector::inspectorCommand(int argc, const char **argv)
{
    if (argc != 1) {
        Tcl_SetResult(interp, TCLCONST("wrong number of args"), TCL_STATIC);
        return TCL_ERROR;
    }

    if (strcmp(argv[0], "cangoback") == 0)
        Tcl_SetResult(interp, TCLCONST(canGoBack() ? "1" : "0"), TCL_STATIC);
    else if (strcmp(argv[0], "cangoforward") == 0)
        Tcl_SetResult(interp, TCLCONST(canGoForward() ? "1" : "0"), TCL_STATIC);
    else if (strcmp(argv[0], "goback") == 0)
        goBack();
    else if (strcmp(argv[0], "goforward") == 0)
        goForward();
    else {
        Tcl_SetResult(interp, TCLCONST("unknown inspector command"), TCL_STATIC);
        return TCL_ERROR;
    }
    return TCL_OK;
}
*/
void Inspector::goUpInto()
{
    QVariant variant = static_cast<QAction *>(QObject::sender())->data();
    if (variant.isValid()) {
        cObject *object = variant.value<cObject*>();
        setObject(object);
    }
}

void Inspector::closeEvent(QCloseEvent *) {
    getQtenv()->deleteInspector(this);
}

QToolBar *Inspector::createToolBarToplevel()
{
    QToolBar *toolbar = new QToolBar();

    // general
    goBackAction = toolbar->addAction(QIcon(":/tools/icons/tools/back.png"), "Back", this, SLOT(goBack()));
    goForwardAction = toolbar->addAction(QIcon(":/tools/icons/tools/forward.png"), "Forward", this, SLOT(goForward()));
    goUpAction = toolbar->addAction(QIcon(":/tools/icons/tools/parent.png"), "Go to parent module", this, SLOT(inspectParent()));
    toolbar->addSeparator();

    QAction *action = toolbar->addAction(QIcon(":/tools/icons/tools/inspectas.png"), "Inspect", this, SLOT(inspectAsPopup()));
    // TODO find out position
    action->setData(QVariant::fromValue(toolbar->pos()));

    action = toolbar->addAction(QIcon(":/tools/icons/tools/copyptr.png"), "Copy name, type or pointer", this, SLOT(namePopup()));
    // TODO find out position
    action->setData(QVariant::fromValue(toolbar->pos()));
    MainWindow *mainWindow = getQtenv()->getMainWindow();
    findObjects = toolbar->addAction(QIcon(":/tools/icons/tools/findobj.png"), "Find objects (CTRL+S)", mainWindow,
                       SLOT(on_actionFindInspectObjects_triggered()));

    return toolbar;
}

void Inspector::inspectAsPopup()
{
    if(!object)
        return;

    QVariant variant = static_cast<QAction *>(QObject::sender())->data();
    if(!variant.isValid())
        return;

    auto typeList = InspectorUtil::supportedInspTypes(object);

    QMenu menu;
    for(auto type : typeList)
    {
        bool state = type == this->type;
        QString label = InspectorUtil::getInspectMenuLabel(type);
        QAction *action = menu.addAction(label, getQtenv(), SLOT(inspect()));
        action->setDisabled(state);
        action->setData(QVariant::fromValue(ActionDataPair(object, type)));
    }

    QPoint point = variant.value<QPoint>();
    menu.exec(mapToGlobal(point));
}

void Inspector::namePopup()
{
    if(!object)
        return;

    QVariant variant = static_cast<QAction *>(QObject::sender())->data();
    if(!variant.isValid())
        return;

    QMenu menu;
    QAction *action = menu.addAction("Copy Pointer With Cast (for Debugger)", getQtenv(), SLOT(utilitiesSubMenu()));
    action->setData(QVariant::fromValue(ActionDataPair(object, InspectorUtil::COPY_PTRWITHCAST)));
    action = menu.addAction("Copy Pointer Value (for Debugger)", getQtenv(), SLOT(utilitiesSubMenu()));
    action->setData(QVariant::fromValue(ActionDataPair(object, InspectorUtil::COPY_PTR)));
    menu.addSeparator();
    action = menu.addAction("Copy Full Path", getQtenv(), SLOT(utilitiesSubMenu()));
    action->setData(QVariant::fromValue(ActionDataPair(object, InspectorUtil::COPY_FULLPATH)));
    action = menu.addAction("Copy Name", getQtenv(), SLOT(utilitiesSubMenu()));
    action->setData(QVariant::fromValue(ActionDataPair(object, InspectorUtil::COPY_FULLNAME)));
    action = menu.addAction("Copy Class Name", getQtenv(), SLOT(utilitiesSubMenu()));
    action->setData(QVariant::fromValue(ActionDataPair(object, InspectorUtil::COPY_CLASSNAME)));

    QPoint point = variant.value<QPoint>();
    menu.exec(mapToGlobal(point));
}

} // namespace qtenv
} // namespace omnetpp

