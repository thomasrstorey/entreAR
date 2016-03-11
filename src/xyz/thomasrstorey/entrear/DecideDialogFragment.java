package xyz.thomasrstorey.entrear;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class DecideDialogFragment extends DialogFragment {
	
	EditText editText;
	DecideDialogListener listener;
	
	public interface DecideDialogListener {
		public void onDialogPositiveClick(DialogFragment dialog, String msg);
	}
	
	
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		LayoutInflater inflater = getActivity().getLayoutInflater();
		final View dialogView = inflater.inflate(R.layout.decide_layout, null);
		builder.setTitle(R.string.decide_message)
			   .setView(dialogView)
			   .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				   @Override
					public void onClick(DialogInterface dialog, int which) {
						listener.onDialogPositiveClick(DecideDialogFragment.this, editText.getText().toString());
					}
			   });
		Dialog dialog = builder.create();
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
					@Override
					public void onShow(final DialogInterface dialog) {
						editText = (EditText) dialogView.findViewById(R.id.decide_text);
						editText.addTextChangedListener(new TextWatcher () {
										public void onTextChanged (CharSequence s, int start, int before, int after){
											if(after > 0){
												((AlertDialog)dialog)
												.getButton(AlertDialog.BUTTON_POSITIVE)
												.setEnabled(true);
											} else {
												((AlertDialog)dialog)
												.getButton(AlertDialog.BUTTON_POSITIVE)
												.setEnabled(false);
											}
										}
										public void beforeTextChanged (CharSequence s, int start, int before, int after){
																					
										}
										public void afterTextChanged (Editable s){
											
										}
									});
					}
			   });
		return dialog;
	}
	
	public void setListener (DecideDialogListener _listener){
		listener = _listener;
	}
}
