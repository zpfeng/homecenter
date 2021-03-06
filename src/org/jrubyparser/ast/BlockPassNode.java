/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2009 Thomas E. Enebo <tom.enebo@gmail.com>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jrubyparser.ast;

import org.jrubyparser.NodeVisitor;
import org.jrubyparser.SourcePosition;

/**
 * Block passed explicitly as an argument in a method call.
 * A block passing argument in a method call (last argument prefixed by an ampersand).
 */
public class BlockPassNode extends Node {
    private Node bodyNode;

    /** Used by the arg_blk_pass and new_call, new_fcall and new_super
     * methods in ParserSupport to temporary save the args node.
     */
    private Node argsNode;

    public BlockPassNode(SourcePosition position, Node bodyNode) {
        super(position);
        this.bodyNode = adopt(bodyNode);
    }


    /**
     * Checks node for 'sameness' for diffing.
     *
     * @param node to be compared to
     * @return Returns a boolean
     */
    @Override
    public boolean isSame(Node node) {
        if (!super.isSame(node)) return false;

        BlockPassNode other = (BlockPassNode) node;

        if (getArgs() == null && other.getArgs() == null) return getBody().isSame(other.getBody());
        if (getArgs() == null || other.getArgs() == null) return false;
            
        return getArgs().isSame(other.getArgs()) && getBody().isSame(other.getBody());
    }


    public NodeType getNodeType() {
        return NodeType.BLOCKPASSNODE;
    }

    /**
     * Accept for the visitor pattern.
     * @param iVisitor the visitor
     **/
    public Object accept(NodeVisitor iVisitor) {
        return iVisitor.visitBlockPassNode(this);
    }

    /**
     * Gets the bodyNode.
     * @return Returns a Node
     */
    @Deprecated
    public Node getBodyNode() {
        return getBody();
    }
    
    public Node getBody() {
        return bodyNode;
    }

    /**
     * Gets the argsNode.
     * @return Returns a IListNode
     */
    @Deprecated
    public Node getArgsNode() {
        return getArgs();
    }
    
    public Node getArgs() {
        return argsNode;
    }

    /**
     * Sets the argsNode.
     * @param argsNode The argsNode to set
     */
    @Deprecated
    public void setArgsNode(Node argsNode) {
        setArgs(argsNode);
    }
    
    public void setArgs(Node argsNode) {
        this.argsNode = adopt(argsNode);
    }
}
