package hc.server.ui.design.hpj;

import hc.App;
import hc.server.HCActionListener;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.MutableTreeNode;

public class NameEditPanel extends NodeEditPanel {
	protected final String nameLabel = "Name";
	
	public NameEditPanel() {
		namePanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		namePanel.add(new JLabel(nameLabel + " : "));
		nameFiled.getDocument().addDocumentListener(new DocumentListener() {
			private void modify(){
				currItem.name = nameFiled.getText();
				notifyModified(true);
			}
			@Override
			public void removeUpdate(final DocumentEvent e) {
				modify();
			}
			
			@Override
			public void insertUpdate(final DocumentEvent e) {
				modify();
			}
			
			@Override
			public void changedUpdate(final DocumentEvent e) {
				modify();
			}
		});
		nameFiled.addActionListener(new HCActionListener(new Runnable() {
			@Override
			public void run() {
				currItem.name = nameFiled.getText();
				App.invokeLaterUI(updateTreeRunnable);
				notifyModified(true);
			}
		}, threadPoolToken));
		nameFiled.setColumns(20);
		namePanel.add(nameFiled);
		
		setLayout(new BorderLayout());
		add(namePanel, BorderLayout.NORTH);
	}
	

	
	protected final JPanel namePanel = new JPanel();
	final JTextField nameFiled = new JTextField();
	
	@Override
	public void init(final MutableTreeNode data, final JTree tree){
		isInited = false;
		super.init(data, tree);
		
		currItem = (HPNode)currNode.getUserObject();
		nameFiled.setText(currItem.name);
		
		extendInit();
		
		try{
			Thread.sleep(500);//等待事件完成
		}catch (final Exception e) {
		}
		
		isInited = true;
	}
	
	public void extendInit(){
		
	}
}
