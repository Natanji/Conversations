package eu.siacs.conversations.ui;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.TreeSet;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnMucRosterUpdate;
import eu.siacs.conversations.xmpp.jid.Jid;

public class ConferenceDetailsActivity extends XmppActivity implements OnConversationUpdate, OnMucRosterUpdate, XmppConnectionService.OnAffiliationChanged, XmppConnectionService.OnRoleChanged, XmppConnectionService.OnConferenceOptionsPushed {
	public static final String ACTION_VIEW_MUC = "view_muc";
	private Conversation mConversation;
    private OnClickListener inviteListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            inviteToConversation(mConversation);
        }
    };
	private TextView mYourNick;
	private ImageView mYourPhoto;
	private ImageButton mEditNickButton;
	private TextView mRoleAffiliaton;
	private TextView mFullJid;
	private TextView mAccountJid;
	private LinearLayout membersView;
	private LinearLayout mMoreDetails;
    private LinearLayout mKeywords;
    private LinearLayout mKeywordList;
	private TextView mConferenceType;
	private TableLayout mConferenceInfoTable;
	private TextView mConferenceInfoMam;
	private TextView mNotifyStatusText;
	private ImageButton mChangeConferenceSettingsButton;
	private ImageButton mNotifyStatusButton;
	private Button mInviteButton;
    private Button mInviteButton;
    private AutoCompleteTextView mKeywordTextInput;
    private ArrayAdapter<String> keyword_autocomplete;
    private ImageButton mKeywordButton;
	private String uuid = null;
	private User mSelectedUser = null;
    private Spinner mNotificationMode;

	private boolean mAdvancedMode = false;

	private UiCallback<Conversation> renameCallback = new UiCallback<Conversation>() {
		@Override
		public void success(Conversation object) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(ConferenceDetailsActivity.this,getString(R.string.your_nick_has_been_changed),Toast.LENGTH_SHORT).show();
					updateView();
				}
			});

		}

		@Override
		public void error(final int errorCode, Conversation object) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(ConferenceDetailsActivity.this,getString(errorCode),Toast.LENGTH_SHORT).show();
				}
			});
		}

		@Override
		public void userInputRequried(PendingIntent pi, Conversation object) {

		}
	};

	private OnClickListener mNotifyStatusClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			AlertDialog.Builder builder = new AlertDialog.Builder(ConferenceDetailsActivity.this);
			builder.setTitle(R.string.pref_notification_settings);
			String[] choices = {
					getString(R.string.notify_on_all_messages),
					getString(R.string.notify_only_when_highlighted),
					getString(R.string.notify_never)
			};
			final AtomicInteger choice;
			if (mConversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL,0) == Long.MAX_VALUE) {
				choice = new AtomicInteger(2);
			} else {
				choice = new AtomicInteger(mConversation.alwaysNotify() ? 0 : 1);
			}
			builder.setSingleChoiceItems(choices, choice.get(), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					choice.set(which);
				}
			});
			builder.setNegativeButton(R.string.cancel, null);
			builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (choice.get() == 2) {
						mConversation.setMutedTill(Long.MAX_VALUE);
					} else {
						mConversation.setMutedTill(0);
						mConversation.setAttribute(Conversation.ATTRIBUTE_ALWAYS_NOTIFY,String.valueOf(choice.get() == 0));
					}
					xmppConnectionService.updateConversation(mConversation);
					updateView();
				}
			});
			builder.create().show();
		}
	};

	private OnClickListener mChangeConferenceSettings = new OnClickListener() {
		@Override
		public void onClick(View v) {
			final MucOptions mucOptions = mConversation.getMucOptions();
			AlertDialog.Builder builder = new AlertDialog.Builder(ConferenceDetailsActivity.this);
			builder.setTitle(R.string.conference_options);
			final String[] options;
			final boolean[] values;
			if (mAdvancedMode) {
				options = new String[]{
						getString(R.string.members_only),
						getString(R.string.moderated),
						getString(R.string.non_anonymous)
				};
				values = new boolean[]{
						mucOptions.membersOnly(),
						mucOptions.moderated(),
						mucOptions.nonanonymous()
				};
			} else {
				options = new String[]{
						getString(R.string.members_only),
						getString(R.string.non_anonymous)
				};
				values = new boolean[]{
						mucOptions.membersOnly(),
						mucOptions.nonanonymous()
				};
			}
			builder.setMultiChoiceItems(options,values,new DialogInterface.OnMultiChoiceClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which, boolean isChecked) {
					values[which] = isChecked;
				}
			});
			builder.setNegativeButton(R.string.cancel, null);
			builder.setPositiveButton(R.string.confirm,new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (!mucOptions.membersOnly() && values[0]) {
						xmppConnectionService.changeAffiliationsInConference(mConversation,
								MucOptions.Affiliation.NONE,
								MucOptions.Affiliation.MEMBER);
					}
					Bundle options = new Bundle();
					options.putString("muc#roomconfig_membersonly", values[0] ? "1" : "0");
					if (values.length == 2) {
						options.putString("muc#roomconfig_whois", values[1] ? "anyone" : "moderators");
					} else if (values.length == 3) {
						options.putString("muc#roomconfig_moderatedroom", values[1] ? "1" : "0");
						options.putString("muc#roomconfig_whois", values[2] ? "anyone" : "moderators");
					}
					options.putString("muc#roomconfig_persistentroom", "1");
					xmppConnectionService.pushConferenceConfiguration(mConversation,
							options,
							ConferenceDetailsActivity.this);
				}
			});
			builder.create().show();
		}
	};
	private OnValueEdited onSubjectEdited = new OnValueEdited() {

		@Override
		public void onValueEdited(String value) {
			xmppConnectionService.pushSubjectToConference(mConversation,value);
		}
	};

	@Override
	public void onConversationUpdate() {
		refreshUi();
	}

	@Override
	public void onMucRosterUpdate() {
		refreshUi();
	}

	@Override
	protected void refreshUiReal() {
		updateView();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_muc_details);
		mYourNick = (TextView) findViewById(R.id.muc_your_nick);
		mYourPhoto = (ImageView) findViewById(R.id.your_photo);
		mEditNickButton = (ImageButton) findViewById(R.id.edit_nick_button);
		mFullJid = (TextView) findViewById(R.id.muc_jabberid);
		membersView = (LinearLayout) findViewById(R.id.muc_members);
		mAccountJid = (TextView) findViewById(R.id.details_account);
		mMoreDetails = (LinearLayout) findViewById(R.id.muc_more_details);
		mMoreDetails.setVisibility(View.GONE);
        mKeywords = (LinearLayout) findViewById(R.id.muc_keywords);
        mKeywords.setVisibility(View.GONE);
        mKeywordList = (LinearLayout) findViewById(R.id.muc_keyword_list);
        mNotificationMode = (Spinner) findViewById(R.id.notification_mode);
        ArrayAdapter<CharSequence> modeAdapter = ArrayAdapter.createFromResource(this,
                R.array.muc_notification_options, android.R.layout.simple_spinner_item);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mNotificationMode.setAdapter(modeAdapter);
		mChangeConferenceSettingsButton = (ImageButton) findViewById(R.id.change_conference_button);
		mChangeConferenceSettingsButton.setOnClickListener(this.mChangeConferenceSettings);
        mInviteButton = (Button) findViewById(R.id.invite);
        mInviteButton.setOnClickListener(inviteListener);
        keyword_autocomplete = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<String>());
        mKeywordTextInput = (AutoCompleteTextView) findViewById(R.id.input_keyword);
        mKeywordTextInput.setAdapter(keyword_autocomplete);
        mKeywordTextInput.setThreshold(1);
        mKeywordButton = (ImageButton) findViewById(R.id.add_keyword);
		mConferenceType = (TextView) findViewById(R.id.muc_conference_type);
		if (getActionBar() != null) {
			getActionBar().setHomeButtonEnabled(true);
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
        /*
        since setThreshold(1) does not give the desired result of *always* showing completions,
        we use the following hack to achieve a simulated setThreshold(0) this:
        */
        mKeywordTextInput.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mKeywordTextInput.showDropDown();
                return false;
            }
        });
        mKeywordTextInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView exampleView, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mKeywordButton.performClick();
                    return true;
                }
                return false;
            }
        });
		mEditNickButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				quickEdit(mConversation.getMucOptions().getActualNick(),
						new OnValueEdited() {

							@Override
							public void onValueEdited(String value) {
								xmppConnectionService.renameInMuc(mConversation, value, renameCallback);
							}
						});
			}
		});
		mKeywordButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String value = mKeywordTextInput.getText().toString();
				mConversation.getMucOptions().addKeyword(value);
				xmppConnectionService.updateConversation(mConversation);
				mKeywordTextInput.setText("");
				updateView();
			}
		});
		mNotificationMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				MucOptions.NotificationMode prev = mConversation.getMucOptions().getNotificationMode();
				if (prev.getValue() != position) {
					mConversation.getMucOptions().setNotificationMode(position);
					xmppConnectionService.updateConversation(mConversation);
					mKeywordTextInput.requestFocus();
				}

				if (position != MucOptions.NotificationMode.KEYWORDS.getValue()) {
					mKeywords.setVisibility(View.GONE);
				} else {
					mKeywords.setVisibility(View.VISIBLE);
					updateView();
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
                /* nothing to do */
			}
		});
		this.mAdvancedMode = getPreferences().getBoolean("advanced_muc_mode", false);
		this.mConferenceInfoTable = (TableLayout) findViewById(R.id.muc_info_more);
		mConferenceInfoTable.setVisibility(this.mAdvancedMode ? View.VISIBLE : View.GONE);
		this.mConferenceInfoMam = (TextView) findViewById(R.id.muc_info_mam);
		this.mNotifyStatusButton = (ImageButton) findViewById(R.id.notification_status_button);
		this.mNotifyStatusButton.setOnClickListener(this.mNotifyStatusClickListener);
		this.mNotifyStatusText = (TextView) findViewById(R.id.notification_status_text);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		switch (menuItem.getItemId()) {
			case android.R.id.home:
				finish();
				break;
			case R.id.action_edit_subject:
				if (mConversation != null) {
					quickEdit(mConversation.getName(),this.onSubjectEdited);
				}
				break;
			case R.id.action_save_as_bookmark:
				saveAsBookmark();
				break;
			case R.id.action_delete_bookmark:
				deleteBookmark();
				break;
			case R.id.action_advanced_mode:
				this.mAdvancedMode = !menuItem.isChecked();
				menuItem.setChecked(this.mAdvancedMode);
				getPreferences().edit().putBoolean("advanced_muc_mode", mAdvancedMode).commit();
				mConferenceInfoTable.setVisibility(this.mAdvancedMode ? View.VISIBLE : View.GONE);
				invalidateOptionsMenu();
				updateView();
				break;
		}
		return super.onOptionsItemSelected(menuItem);
	}

	@Override
	protected String getShareableUri() {
		if (mConversation != null) {
			return "xmpp:" + mConversation.getJid().toBareJid().toString() + "?join";
		} else {
			return "";
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem menuItemSaveBookmark = menu.findItem(R.id.action_save_as_bookmark);
		MenuItem menuItemDeleteBookmark = menu.findItem(R.id.action_delete_bookmark);
		MenuItem menuItemAdvancedMode = menu.findItem(R.id.action_advanced_mode);
		MenuItem menuItemChangeSubject = menu.findItem(R.id.action_edit_subject);
		menuItemAdvancedMode.setChecked(mAdvancedMode);
		if (mConversation == null) {
			return true;
		}
		Account account = mConversation.getAccount();
		if (account.hasBookmarkFor(mConversation.getJid().toBareJid())) {
			menuItemSaveBookmark.setVisible(false);
			menuItemDeleteBookmark.setVisible(true);
		} else {
			menuItemDeleteBookmark.setVisible(false);
			menuItemSaveBookmark.setVisible(true);
		}
		menuItemChangeSubject.setVisible(mConversation.getMucOptions().canChangeSubject());
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.muc_details, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		Object tag = v.getTag();
		if (tag instanceof User) {
			getMenuInflater().inflate(R.menu.muc_details_context,menu);
			final User user = (User) tag;
			final User self = mConversation.getMucOptions().getSelf();
			this.mSelectedUser = user;
			String name;
			final Contact contact = user.getContact();
			if (contact != null) {
				name = contact.getDisplayName();
			} else if (user.getJid() != null){
				name = user.getJid().toBareJid().toString();
			} else {
				name = user.getName();
			}
			menu.setHeaderTitle(name);
			if (user.getJid() != null) {
				MenuItem showContactDetails = menu.findItem(R.id.action_contact_details);
				MenuItem startConversation = menu.findItem(R.id.start_conversation);
				MenuItem giveMembership = menu.findItem(R.id.give_membership);
				MenuItem removeMembership = menu.findItem(R.id.remove_membership);
				MenuItem giveAdminPrivileges = menu.findItem(R.id.give_admin_privileges);
				MenuItem removeAdminPrivileges = menu.findItem(R.id.remove_admin_privileges);
				MenuItem removeFromRoom = menu.findItem(R.id.remove_from_room);
				MenuItem banFromConference = menu.findItem(R.id.ban_from_conference);
				startConversation.setVisible(true);
				if (contact != null) {
					showContactDetails.setVisible(true);
				}
				if (self.getAffiliation().ranks(MucOptions.Affiliation.ADMIN) &&
						self.getAffiliation().outranks(user.getAffiliation())) {
					if (mAdvancedMode) {
						if (user.getAffiliation() == MucOptions.Affiliation.NONE) {
							giveMembership.setVisible(true);
						} else {
							removeMembership.setVisible(true);
						}
						banFromConference.setVisible(true);
					} else {
						removeFromRoom.setVisible(true);
					}
					if (user.getAffiliation() != MucOptions.Affiliation.ADMIN) {
						giveAdminPrivileges.setVisible(true);
					} else {
						removeAdminPrivileges.setVisible(true);
					}
				}
			} else {
				MenuItem sendPrivateMessage = menu.findItem(R.id.send_private_message);
				sendPrivateMessage.setVisible(true);
			}

		}
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_contact_details:
				Contact contact = mSelectedUser.getContact();
				if (contact != null) {
					switchToContactDetails(contact);
				}
				return true;
			case R.id.start_conversation:
				startConversation(mSelectedUser);
				return true;
			case R.id.give_admin_privileges:
				xmppConnectionService.changeAffiliationInConference(mConversation,mSelectedUser.getJid(), MucOptions.Affiliation.ADMIN,this);
				return true;
			case R.id.give_membership:
				xmppConnectionService.changeAffiliationInConference(mConversation,mSelectedUser.getJid(), MucOptions.Affiliation.MEMBER,this);
				return true;
			case R.id.remove_membership:
				xmppConnectionService.changeAffiliationInConference(mConversation,mSelectedUser.getJid(), MucOptions.Affiliation.NONE,this);
				return true;
			case R.id.remove_admin_privileges:
				xmppConnectionService.changeAffiliationInConference(mConversation,mSelectedUser.getJid(), MucOptions.Affiliation.MEMBER,this);
				return true;
			case R.id.remove_from_room:
				removeFromRoom(mSelectedUser);
				return true;
			case R.id.ban_from_conference:
				xmppConnectionService.changeAffiliationInConference(mConversation,mSelectedUser.getJid(), MucOptions.Affiliation.OUTCAST,this);
				xmppConnectionService.changeRoleInConference(mConversation,mSelectedUser.getName(), MucOptions.Role.NONE,this);
				return true;
			case R.id.send_private_message:
				privateMsgInMuc(mConversation,mSelectedUser.getName());
				return true;
			default:
				return super.onContextItemSelected(item);
		}
	}

	private void removeFromRoom(final User user) {
		if (mConversation.getMucOptions().membersOnly()) {
			xmppConnectionService.changeAffiliationInConference(mConversation,user.getJid(), MucOptions.Affiliation.NONE,this);
			xmppConnectionService.changeRoleInConference(mConversation,mSelectedUser.getName(), MucOptions.Role.NONE,ConferenceDetailsActivity.this);
		} else {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.ban_from_conference);
			builder.setMessage(getString(R.string.removing_from_public_conference,user.getName()));
			builder.setNegativeButton(R.string.cancel,null);
			builder.setPositiveButton(R.string.ban_now,new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					xmppConnectionService.changeAffiliationInConference(mConversation,user.getJid(), MucOptions.Affiliation.OUTCAST,ConferenceDetailsActivity.this);
					xmppConnectionService.changeRoleInConference(mConversation,mSelectedUser.getName(), MucOptions.Role.NONE,ConferenceDetailsActivity.this);
				}
			});
			builder.create().show();
		}
	}

	protected void startConversation(User user) {
		if (user.getJid() != null) {
			Conversation conversation = xmppConnectionService.findOrCreateConversation(this.mConversation.getAccount(),user.getJid().toBareJid(),false);
			switchToConversation(conversation);
		}
	}

	protected void saveAsBookmark() {
		Account account = mConversation.getAccount();
		Bookmark bookmark = new Bookmark(account, mConversation.getJid().toBareJid());
		if (!mConversation.getJid().isBareJid()) {
			bookmark.setNick(mConversation.getJid().getResourcepart());
		}
		bookmark.setBookmarkName(mConversation.getMucOptions().getSubject());
		bookmark.setAutojoin(getPreferences().getBoolean("autojoin",true));
		account.getBookmarks().add(bookmark);
		xmppConnectionService.pushBookmarks(account);
		mConversation.setBookmark(bookmark);
	}

	protected void deleteBookmark() {
		Account account = mConversation.getAccount();
		Bookmark bookmark = mConversation.getBookmark();
		bookmark.unregisterConversation();
		account.getBookmarks().remove(bookmark);
		xmppConnectionService.pushBookmarks(account);
	}

	@Override
	void onBackendConnected() {
		if (mPendingConferenceInvite != null) {
			mPendingConferenceInvite.execute(this);
			mPendingConferenceInvite = null;
		}
		if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
			this.uuid = getIntent().getExtras().getString("uuid");
		}
		if (uuid != null) {
			this.mConversation = xmppConnectionService
				.findConversationByUuid(uuid);
			if (this.mConversation != null) {
				updateView();
			}
		}
	}

	private void updateView() {
		final MucOptions mucOptions = mConversation.getMucOptions();
		final User self = mucOptions.getSelf();
		String account;
		if (Config.DOMAIN_LOCK != null) {
			account = mConversation.getAccount().getJid().getLocalpart();
		} else {
			account = mConversation.getAccount().getJid().toBareJid().toString();
		}
		mAccountJid.setText(getString(R.string.using_account, account));
		mYourPhoto.setImageBitmap(avatarService().get(mConversation.getAccount(), getPixel(48)));
		setTitle(mConversation.getName());
		if (Config.LOCK_DOMAINS_IN_CONVERSATIONS && mConversation.getJid().getDomainpart().equals(Config.CONFERENCE_DOMAIN_LOCK)) {
			mFullJid.setText(mConversation.getJid().getLocalpart());
		} else {
			mFullJid.setText(mConversation.getJid().toBareJid().toString());
		}
		mYourNick.setText(mucOptions.getActualNick());
		mRoleAffiliaton = (TextView) findViewById(R.id.muc_role);
		if (mucOptions.online()) {
			mMoreDetails.setVisibility(View.VISIBLE);
			final String status = getStatus(self);
			if (status != null) {
				mRoleAffiliaton.setVisibility(View.VISIBLE);
				mRoleAffiliaton.setText(status);
			} else {
				mRoleAffiliaton.setVisibility(View.GONE);
			}
			if (mucOptions.membersOnly()) {
				mConferenceType.setText(R.string.private_conference);
			} else {
				mConferenceType.setText(R.string.public_conference);
			}
			if (mucOptions.mamSupport()) {
				mConferenceInfoMam.setText(R.string.server_info_available);
			} else {
				mConferenceInfoMam.setText(R.string.server_info_unavailable);
			}
			if (self.getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
				mChangeConferenceSettingsButton.setVisibility(View.VISIBLE);
			} else {
				mChangeConferenceSettingsButton.setVisibility(View.GONE);
			}
		}

		long mutedTill = mConversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL,0);
		if (mutedTill == Long.MAX_VALUE) {
			mNotifyStatusText.setText(R.string.notify_never);
			mNotifyStatusButton.setImageResource(R.drawable.ic_notifications_off_grey600_24dp);
		} else if (System.currentTimeMillis() < mutedTill) {
			mNotifyStatusText.setText(R.string.notify_paused);
			mNotifyStatusButton.setImageResource(R.drawable.ic_notifications_paused_grey600_24dp);
		} else if (mConversation.alwaysNotify()) {
			mNotifyStatusButton.setImageResource(R.drawable.ic_notifications_grey600_24dp);
			mNotifyStatusText.setText(R.string.notify_on_all_messages);
		} else {
			mNotifyStatusButton.setImageResource(R.drawable.ic_notifications_none_grey600_24dp);
			mNotifyStatusText.setText(R.string.notify_only_when_highlighted);
		}

        if (mKeywords.getVisibility() != View.GONE) {
            //we use TreeSet because it will automatically sort our set
            TreeSet<String> keywords_from_all_conversations = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Conversation conversation : xmppConnectionService.getConversations()) {
                keywords_from_all_conversations.addAll(conversation.getMucOptions().getKeywordsList());
            }
            keywords_from_all_conversations.removeAll(mConversation.getMucOptions().getKeywordsList());
            keyword_autocomplete.clear();
            keyword_autocomplete.addAll(keywords_from_all_conversations);
        }
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		membersView.removeAllViews();
		final ArrayList<User> users = mucOptions.getUsers();
		Collections.sort(users,new Comparator<User>() {
			@Override
			public int compare(User lhs, User rhs) {
				return lhs.getName().compareToIgnoreCase(rhs.getName());
			}
		});
		for (final User user : users) {
			View view = inflater.inflate(R.layout.contact, membersView,false);
			this.setListItemBackgroundOnView(view);
			view.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					highlightInMuc(mConversation, user.getName());
				}
			});
			registerForContextMenu(view);
			view.setTag(user);
			TextView tvDisplayName = (TextView) view.findViewById(R.id.contact_display_name);
			TextView tvKey = (TextView) view.findViewById(R.id.key);
			TextView tvStatus = (TextView) view.findViewById(R.id.contact_jid);
			if (mAdvancedMode && user.getPgpKeyId() != 0) {
				tvKey.setVisibility(View.VISIBLE);
				tvKey.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						viewPgpKey(user);
					}
				});
				tvKey.setText(OpenPgpUtils.convertKeyIdToHex(user.getPgpKeyId()));
			}
			Contact contact = user.getContact();
			if (contact != null) {
				tvDisplayName.setText(contact.getDisplayName());
				tvStatus.setText(user.getName() + " \u2022 " + getStatus(user));
			} else {
				tvDisplayName.setText(user.getName());
				tvStatus.setText(getStatus(user));

			}
			ImageView iv = (ImageView) view.findViewById(R.id.contact_photo);
			iv.setImageBitmap(avatarService().get(user, getPixel(48), false));
			membersView.addView(view);
			if (mConversation.getMucOptions().canInvite()) {
				mInviteButton.setVisibility(View.VISIBLE);
			} else {
				mInviteButton.setVisibility(View.GONE);
			}
		}
        mNotificationMode.setSelection(mucOptions.getNotificationMode().getValue());
        mKeywordList.removeAllViews();
        final ArrayList<String> keywords = new ArrayList<>();
        keywords.addAll(mucOptions.getKeywordsList());
        Collections.sort(keywords,String.CASE_INSENSITIVE_ORDER);
        for (final String keyword : keywords) {
            View view = inflater.inflate(R.layout.keyword, mKeywords, false);
            this.setListItemBackgroundOnView(view);
            registerForContextMenu(view);
            view.setTag(keyword);
            final TextView tvKeyword = (TextView) view.findViewById(R.id.keyword_item);
            final ImageButton ibEdit = (ImageButton) view.findViewById(R.id.keyword_edit);
            final ImageButton ibDelete = (ImageButton) view.findViewById(R.id.keyword_delete);
            tvKeyword.setText(keyword);
            ibEdit.setVisibility(View.GONE);
            ibDelete.setVisibility(View.GONE);

            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    for (int i = 0; i < mKeywordList.getChildCount(); i++) {
                        View keywordLine = mKeywordList.getChildAt(i);
                        keywordLine.findViewById(R.id.keyword_edit).setVisibility(View.GONE);
                        keywordLine.findViewById(R.id.keyword_delete).setVisibility(View.GONE);
                    }
                    ibEdit.setVisibility(View.VISIBLE);
                    ibDelete.setVisibility(View.VISIBLE);
                }
            });
            ibEdit.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    quickEdit(keyword,
                            new OnValueEdited() {

                                @Override
                                public void onValueEdited(String value) {
                                    mucOptions.removeKeyword(keyword);
                                    mucOptions.addKeyword(value);
                                    xmppConnectionService.updateConversation(mConversation);
                                    updateView();
                                }
                            });
                }
            });
            ibDelete.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    mucOptions.removeKeyword(keyword);
                    xmppConnectionService.updateConversation(mConversation);
                    updateView();
                }
            });

            mKeywordList.addView(view);
        }
	}

	private String getStatus(User user) {
		if (mAdvancedMode) {
			StringBuilder builder = new StringBuilder();
			builder.append(getString(user.getAffiliation().getResId()));
			builder.append(" (");
			builder.append(getString(user.getRole().getResId()));
			builder.append(')');
			return builder.toString();
		} else {
			return getString(user.getAffiliation().getResId());
		}
	}

	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void setListItemBackgroundOnView(View view) {
		int sdk = android.os.Build.VERSION.SDK_INT;
		if (sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
			view.setBackgroundDrawable(getResources().getDrawable(R.drawable.greybackground));
		} else {
			view.setBackground(getResources().getDrawable(R.drawable.greybackground));
		}
	}

	private void viewPgpKey(User user) {
		PgpEngine pgp = xmppConnectionService.getPgpEngine();
		if (pgp != null) {
			PendingIntent intent = pgp.getIntentForKey(
					mConversation.getAccount(), user.getPgpKeyId());
			if (intent != null) {
				try {
					startIntentSenderForResult(intent.getIntentSender(), 0,
							null, 0, 0, 0);
				} catch (SendIntentException ignored) {

				}
			}
		}
	}

	@Override
	public void onAffiliationChangedSuccessful(Jid jid) {

	}

	@Override
	public void onAffiliationChangeFailed(Jid jid, int resId) {
		displayToast(getString(resId,jid.toBareJid().toString()));
	}

	@Override
	public void onRoleChangedSuccessful(String nick) {

	}

	@Override
	public void onRoleChangeFailed(String nick, int resId) {
		displayToast(getString(resId,nick));
	}

	@Override
	public void onPushSucceeded() {
		displayToast(getString(R.string.modified_conference_options));
	}

	@Override
	public void onPushFailed() {
		displayToast(getString(R.string.could_not_modify_conference_options));
	}

	private void displayToast(final String msg) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(ConferenceDetailsActivity.this,msg,Toast.LENGTH_SHORT).show();
			}
		});
	}
}
