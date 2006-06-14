package org.omnetpp.ned.editor.graph.model.commands;

import org.eclipse.gef.commands.Command;
import org.eclipse.swt.custom.PopupList;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.omnetpp.ned2.model.CompoundModuleNodeEx;
import org.omnetpp.ned2.model.ConnectionNodeEx;
import org.omnetpp.ned2.model.IConnectable;
import org.omnetpp.ned2.model.IConnectionContainer;
import org.omnetpp.ned2.model.IElement;
import org.omnetpp.ned2.model.SubmoduleNodeEx;

/**
 * (Re)assigns a Connection to srcModule/destModule sub/compound module gates and also adds it to the
 * model (to the compound module's connectios section) (or removes it if the new source or destination is NULL)
 * @author rhornig
 */
public class ConnectionCommand extends Command {

    protected IConnectable oldSrcModule;
    protected String oldSrcGate;
    protected IConnectable oldDestModule;
    protected String oldDestGate;
    protected IConnectable srcModule;
    protected String srcGate;
    protected IConnectable destModule;
    protected String destGate;
    protected ConnectionNodeEx connNode;
    protected ConnectionNodeEx connNodeNextSibling = null;
    protected IConnectionContainer parent = null;

    @Override
    public String getLabel() {
        if (connNode != null && srcModule == null && destModule == null)
            return "Delete connection";
        
        if (connNode != null && srcModule != null && destModule != null)
            return "Create connection";

        return "Move connection";
    }

    public ConnectionCommand (ConnectionNodeEx conn) {
        connNode = conn;
        oldSrcModule = connNode.getSrcModuleRef();
        oldDestModule = connNode.getDestModuleRef();
        oldSrcGate = connNode.getSrcGate();
        oldDestGate = connNode.getDestGate();
    }
    /**
     * Handles which module can be connected to which
     */
    @Override
    public boolean canExecute() {
    	// allow deletion (both module is null)
    	if(srcModule == null && destModule == null)
    		return true;
    	// we can connect two submodules module ONLY if they are siblings (ie they have the same parent)
    	IConnectable sMod = srcModule != null ? srcModule : connNode.getSrcModuleRef();
    	IConnectable dMod = destModule != null ? destModule : connNode.getDestModuleRef();

    	if(sMod instanceof SubmoduleNodeEx && dMod instanceof SubmoduleNodeEx &&
    			((IElement)sMod).getParent() == ((IElement)dMod).getParent()) 
    		return true;
    	
    	// if one module is bubmodule and the other is compound, the compound module MUST contain the submodule
    	if (sMod instanceof CompoundModuleNodeEx && dMod instanceof SubmoduleNodeEx &&
    			((SubmoduleNodeEx)dMod).getCompoundModule() == sMod)
    		return true;
    	
    	if (dMod instanceof CompoundModuleNodeEx && sMod instanceof SubmoduleNodeEx &&
    			((SubmoduleNodeEx)sMod).getCompoundModule() == dMod)
    		return true;
    	
    	// do not allow command inany other cases
        return false;
    }

    @Override
    public void execute() {
   		// do the changes
    	redo();

    	// after we connected the modules, let's ask the user which gates should be connected
    	if(srcModule != null || destModule != null) {
    		// ask the user for ser and dest gate names
    		askForGates(srcModule != null, destModule != null);

    		// if both gates are specified by the user do the model change
    		if (srcGate != null && destGate != null) 
    			redo();
    		else //otherwise revert the connection change (user cancel) 
    			undo();
    	}
    }

    @Override
    public void redo() {
        if (srcModule != null && oldSrcModule != srcModule) 
            connNode.setSrcModuleRef(srcModule);
        
        if (srcGate != null && oldSrcGate != srcGate)
            connNode.setSrcGate(srcGate);
        
        if (destModule != null && oldDestModule != destModule) 
            connNode.setDestModuleRef(destModule);
        
        if (destGate != null && oldDestGate != destGate)
            connNode.setDestGate(destGate);
        
        // if both src and dest module should be detached then remove it 
        // from the model totally (ie delete it)
        if (srcModule == null && destModule == null) {
            // just store the NEXT sibling so we can put it back during undo to the right place
            connNodeNextSibling = (ConnectionNodeEx)connNode.getNextConnectionNodeSibling();
            // store the parent too so we now where to put it back during undo
            parent = (IConnectionContainer)connNode.getParent().getParent();
            // now detach from both src and dest modules
            connNode.setSrcModuleRef(null);
            connNode.setDestModuleRef(null);
            // and remove from the parent too
           	connNode.removeFromParent();
        }
    }

    @Override
    public void undo() {
        // if it was removed from the model, put it back
        if (connNode.getParent() == null && parent != null)
            parent.insertConnection(connNodeNextSibling, connNode);
        
        // attach to the original modules and gates
        connNode.setSrcModuleRef(oldSrcModule);
        connNode.setSrcGate(oldSrcGate);
        connNode.setDestModuleRef(oldDestModule);
        connNode.setDestGate(oldDestGate);
    }

    public void setSrcModule(IConnectable newSrcModule) {
        srcModule = newSrcModule;
    }

    public void setSrcGate(String newSrcGate) {
        srcGate = newSrcGate;
    }

    public void setDestModule(IConnectable newDestModule) {
        destModule = newDestModule;
    }

    public void setDestGate(String newDestGate) {
        destGate = newDestGate;
    }

	public String getDestGate() {
		return destGate;
	}

	public IConnectable getDestModule() {
		return destModule;
	}

	public String getSrcGate() {
		return srcGate;
	}

	public IConnectable getSrcModule() {
		return srcModule;
	}
	
	/**
	 * This method ask the user which gates should be connected on the source and destination module
	 * @param askForSrcGate
	 * @param askForDestGate
	 */
	protected void askForGates(boolean askForSrcGate, boolean askForDestGate) {
		// do not ask anything 
		if (!askForSrcGate && !askForDestGate)
			return;
		
		PopupList pl = new PopupList(Display.getCurrent().getActiveShell());
		String proba[] = new String[] {"INPUTPORT11","IN2","OUT1","OUT2"};
		// TODO get the needed in/out connections from the model directly
		// if both of them are requred create pairs
		// return only free gates
		pl.setItems(proba);
		pl.setMinimumWidth(50);
		Point pt = Display.getCurrent().getCursorLocation();
		String result = pl.open(new Rectangle(pt.x, pt.y, 50, 50));

		// get the src and dest gatemenes from the selection
		// just temportary testing
		destGate = result;
		srcGate = result;
	}
}
