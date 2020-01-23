package dev.snowdrop.example.service;

import java.io.IOError;
import java.io.IOException;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.springframework.stereotype.Component;

@Path("/greeting")
@Component
public class GreetingEndpoint {

    private static Client client = null;

    //private static TdApi.AuthorizationState authorizationState = null;
    //private static volatile boolean haveAuthorization = false;
    private static volatile boolean quiting = false;

    private static final Client.ResultHandler defaultHandler = new DefaultHandler();

    //private static final Lock authorizationLock = new ReentrantLock();
    //private static final Condition gotAuthorization = authorizationLock.newCondition();

    private static final ConcurrentMap<Integer, TdApi.User> users = new ConcurrentHashMap<Integer, TdApi.User>();
    private static final ConcurrentMap<Integer, TdApi.BasicGroup> basicGroups = new ConcurrentHashMap<Integer, TdApi.BasicGroup>();
    private static final ConcurrentMap<Integer, TdApi.Supergroup> supergroups = new ConcurrentHashMap<Integer, TdApi.Supergroup>();
    private static final ConcurrentMap<Integer, TdApi.SecretChat> secretChats = new ConcurrentHashMap<Integer, TdApi.SecretChat>();

    private static final ConcurrentMap<Long, TdApi.Chat> chats = new ConcurrentHashMap<Long, TdApi.Chat>();
    private static final NavigableSet<OrderedChat> mainChatList = new TreeSet<OrderedChat>();
    //private static boolean haveFullMainChatList = false;
    
    private static final ConcurrentMap<Integer, TdApi.UserFullInfo> usersFullInfo = new ConcurrentHashMap<Integer, TdApi.UserFullInfo>();
    private static final ConcurrentMap<Integer, TdApi.BasicGroupFullInfo> basicGroupsFullInfo = new ConcurrentHashMap<Integer, TdApi.BasicGroupFullInfo>();
    private static final ConcurrentMap<Integer, TdApi.SupergroupFullInfo> supergroupsFullInfo = new ConcurrentHashMap<Integer, TdApi.SupergroupFullInfo>();

    private static final String newLine = System.getProperty("line.separator");
    //private static final String commandsLine = "Enter command (gcs - GetChats, gc <chatId> - GetChat, me - GetMe, sm <chatId> <message> - SendMessage, lo - LogOut, q - Quit): ";
    private static volatile String currentPrompt = null;
    
    private static final ConcurrentMap<String, AuthorizationRequestHandler> authHandlers = new ConcurrentHashMap<String, AuthorizationRequestHandler>();
    
    static {
        try {
            System.out.println("java.library.path: " + System.getProperty("java.library.path"));  
            System.loadLibrary("tdjni");
        
            System.out.println("Initializing tdlib client...");
            client = Client.create(null, null, null);
            Client.execute(new TdApi.SetLogVerbosityLevel(1));
    		if (Client.execute(new TdApi.SetLogStream(new TdApi.LogStreamFile("tdlib.log", 1 << 27))) instanceof TdApi.Error) {
    			throw new IOError(new IOException("Write access to the current directory is required"));
    		}
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
    }
    
    @GET
    @Path("initAuth")
    @Produces("application/json")
    public Greeting greeting(@QueryParam("phoneNumber") @DefaultValue("unknown") String phoneNumber) {
        
    	if (!phoneNumber.equals("unknown")) {
    		AuthorizationRequestHandler authorizationRequestHandler = new AuthorizationRequestHandler(phoneNumber);
    		authHandlers.put(phoneNumber, authorizationRequestHandler);
    		client.setUpdatesHandler(new UpdatesHandler(phoneNumber), new ExceptionHandler());
    	}
    	
        final String message = String.format(Greeting.FORMAT, phoneNumber);
        return new Greeting(message);
    }

    @GET
    @Path("authCode")
    @Produces("application/json")
    public Greeting code(@QueryParam("phoneNumber") @DefaultValue("unknown") String phoneNumber, @QueryParam("code") @DefaultValue("0") String code) {

        if (!phoneNumber.equals("unknown") && !code.equals("0")) {
        	AuthorizationRequestHandler authorizationRequestHandler = authHandlers.get(phoneNumber);
        	if (authorizationRequestHandler != null) {
        		authorizationRequestHandler.setCode(code);
        	}
        	client.setUpdatesHandler(new UpdatesHandler(phoneNumber, code), new ExceptionHandler());
        }

        final String message = String.format(Greeting.FORMAT, phoneNumber);
        return new Greeting(message);
    }
    
    private static void onAuthorizationStateUpdated(TdApi.AuthorizationState authorizationState, String phoneNumber, String code) {
        //if (authorizationState != null) {
        //    GreetingEndpoint.authorizationState = authorizationState;
        //}
        print("Updated authn state to " + authorizationState.toString() + " for " + phoneNumber + ":" + code);
        switch (authorizationState.getConstructor()) {
            case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
                TdApi.TdlibParameters parameters = new TdApi.TdlibParameters();
                parameters.databaseDirectory = "tdlibdb";
                parameters.useMessageDatabase = true;
                parameters.useSecretChats = true;
                parameters.apiId = 94575;
                parameters.apiHash = "a3406de8d171bb422bb6ddf3bbd800e2";
                parameters.systemLanguageCode = "en";
                parameters.deviceModel = "OpenShift Online";
                parameters.systemVersion = "RHEL7";
                parameters.applicationVersion = "1.0";
                parameters.enableStorageOptimizer = true;
                client.send(new TdApi.SetTdlibParameters(parameters), authHandlers.get(phoneNumber));
                break;
            case TdApi.AuthorizationStateWaitEncryptionKey.CONSTRUCTOR:
                client.send(new TdApi.CheckDatabaseEncryptionKey(), authHandlers.get(phoneNumber));
                break;
            case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR: {
                //String phoneNumber = promptString("Please enter phone number: ");
                client.send(new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), authHandlers.get(phoneNumber));
                break;
            }
            case TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR: {
            	System.out.println("Not implemented!");
                //String link = ((TdApi.AuthorizationStateWaitOtherDeviceConfirmation) authorizationState).link;
                //System.out.println("Please confirm this login link on another device: " + link);
                break;
            }
            case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR: {
                //String code = promptString("Please enter authentication code: ");
                client.send(new TdApi.CheckAuthenticationCode(code), authHandlers.get(phoneNumber));
                break;
            }
            case TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR: {
            	System.out.println("Not implemented!");
                //String firstName = promptString("Please enter your first name: ");
                //String lastName = promptString("Please enter your last name: ");
                //client.send(new TdApi.RegisterUser(firstName, lastName), new AuthorizationRequestHandler());
                break;
            }
            case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR: {
            	System.out.println("Not implemented!");
                //String password = promptString("Please enter password: ");
                //client.send(new TdApi.CheckAuthenticationPassword(password), new AuthorizationRequestHandler());
                break;
            }
            case TdApi.AuthorizationStateReady.CONSTRUCTOR:
                //haveAuthorization = true;
                //authorizationLock.lock();
                //try {
                //    gotAuthorization.signal();
                //} finally {
                //    authorizationLock.unlock();
                //}
                client.send(new TdApi.GetMe(), defaultHandler);
                break;
            case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR:
                //haveAuthorization = false;
            	authHandlers.remove(phoneNumber);
            	print("Logging out");
                break;
            case TdApi.AuthorizationStateClosing.CONSTRUCTOR:
                //haveAuthorization = false;
                print("Closing");
                break;
            case TdApi.AuthorizationStateClosed.CONSTRUCTOR:
                print("Closed");
                if (!quiting) {
                    client = Client.create(null, null, null); // recreate client after previous has closed
                }
                break;
            default:
                System.err.println("Unsupported authorization state:" + newLine + authorizationState);
        }
    }

    /*private static int toInt(String arg) {
        int result = 0;
        try {
            result = Integer.parseInt(arg);
        } catch (NumberFormatException ignored) {
        }
        return result;
    }*/

    /*private static long getChatId(String arg) {
        long chatId = 0;
        try {
            chatId = Long.parseLong(arg);
        } catch (NumberFormatException ignored) {
        }
        return chatId;
    }*/

    /*private static String promptString(String prompt) {
        System.out.print(prompt);
        currentPrompt = prompt;
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String str = "";
        try {
            str = reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        currentPrompt = null;
        return str;
    }*/

    /*private static void getCommand() {
        String command = promptString(commandsLine);
        String[] commands = command.split(" ", 2);
        try {
            switch (commands[0]) {
                case "gcs": {
                    int limit = 20;
                    if (commands.length > 1) {
                        limit = toInt(commands[1]);
                    }
                    getMainChatList(limit);
                    break;
                }
                case "gc":
                    client.send(new TdApi.GetChat(getChatId(commands[1])), defaultHandler);
                    break;
                case "me":
                    client.send(new TdApi.GetMe(), defaultHandler);
                    break;
                case "sm": {
                    String[] args = commands[1].split(" ", 2);
                    sendMessage(getChatId(args[0]), args[1]);
                    break;
                }
                case "lo":
                    //haveAuthorization = false;
                    client.send(new TdApi.LogOut(), defaultHandler);
                    break;
                case "q":
                    quiting = true;
                    //haveAuthorization = false;
                    client.send(new TdApi.Close(), defaultHandler);
                    break;
                default:
                    System.err.println("Unsupported command: " + command);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            print("Not enough arguments");
        }
    }*/

    /*private static void getMainChatList(final int limit) {
        synchronized (mainChatList) {
            if (!haveFullMainChatList && limit > mainChatList.size()) {
                // have enough chats in the chat list or chat list is too small
                long offsetOrder = Long.MAX_VALUE;
                long offsetChatId = 0;
                if (!mainChatList.isEmpty()) {
                    OrderedChat last = mainChatList.last();
                    offsetOrder = last.order;
                    offsetChatId = last.chatId;
                }
                client.send(new TdApi.GetChats(new TdApi.ChatListMain(), offsetOrder, offsetChatId, limit - mainChatList.size()), new Client.ResultHandler() {
                    @Override
                    public void onResult(TdApi.Object object) {
                        switch (object.getConstructor()) {
                            case TdApi.Error.CONSTRUCTOR:
                                System.err.println("Receive an error for GetChats:" + newLine + object);
                                break;
                            case TdApi.Chats.CONSTRUCTOR:
                                long[] chatIds = ((TdApi.Chats) object).chatIds;
                                if (chatIds.length == 0) {
                                    synchronized (mainChatList) {
                                        haveFullMainChatList = true;
                                    }
                                }
                                // chats had already been received through updates, let's retry request
                                getMainChatList(limit);
                                break;
                            default:
                                System.err.println("Receive wrong response from TDLib:" + newLine + object);
                        }
                    }
                });
                return;
            }

            // have enough chats in the chat list to answer request
            java.util.Iterator<OrderedChat> iter = mainChatList.iterator();
            System.out.println();
            System.out.println("First " + limit + " chat(s) out of " + mainChatList.size() + " known chat(s):");
            for (int i = 0; i < limit; i++) {
                long chatId = iter.next().chatId;
                TdApi.Chat chat = chats.get(chatId);
                synchronized (chat) {
                    System.out.println(chatId + ": " + chat.title);
                }
            }
            print("");
        }
    }*/

    /*private static void sendMessage(long chatId, String message) {
        // initialize reply markup just for testing
        TdApi.InlineKeyboardButton[] row = {new TdApi.InlineKeyboardButton("https://telegram.org?1", new TdApi.InlineKeyboardButtonTypeUrl()), new TdApi.InlineKeyboardButton("https://telegram.org?2", new TdApi.InlineKeyboardButtonTypeUrl()), new TdApi.InlineKeyboardButton("https://telegram.org?3", new TdApi.InlineKeyboardButtonTypeUrl())};
        TdApi.ReplyMarkup replyMarkup = new TdApi.ReplyMarkupInlineKeyboard(new TdApi.InlineKeyboardButton[][]{row, row, row});

        TdApi.InputMessageContent content = new TdApi.InputMessageText(new TdApi.FormattedText(message, null), false, true);
        client.send(new TdApi.SendMessage(chatId, 0, null, replyMarkup, content), defaultHandler);
    }*/
    
    private static void print(String str) {
        if (currentPrompt != null) {
            System.out.println("");
        }
        System.out.println(str);
        if (currentPrompt != null) {
            System.out.print(currentPrompt);
        }
    }

    private static void setChatOrder(TdApi.Chat chat, long order) {
        synchronized (mainChatList) {
            synchronized (chat) {
                if (chat.chatList == null || chat.chatList.getConstructor() != TdApi.ChatListMain.CONSTRUCTOR) {
                  return;
                }

                if (chat.order != 0) {
                    boolean isRemoved = mainChatList.remove(new OrderedChat(chat.order, chat.id));
                    assert isRemoved;
                }

                chat.order = order;

                if (chat.order != 0) {
                    boolean isAdded = mainChatList.add(new OrderedChat(chat.order, chat.id));
                    assert isAdded;
                }
            }
        }
    }

    private static class DefaultHandler implements Client.ResultHandler {
        @Override
        public void onResult(TdApi.Object object) {
            print("Received " + object.getClass().getName());  
            print(object.toString());
            if (object instanceof TdApi.User) {
            	TdApi.User me = (TdApi.User)object;
                //315688739 is @device_locator_bot id
                print("Creating group chat for " + me.id + " and @device_locator_bot");
                client.send(new TdApi.CreateNewBasicGroupChat(new int[] {315688739, me.id}, "Device XYZ notifications " + System.currentTimeMillis()) , defaultHandler);
            }
        }
    }

    private static class UpdatesHandler implements Client.ResultHandler {
        
    	private String phoneNumber;
    	
    	private String code;
    	
    	public UpdatesHandler(String phoneNumber) {
    		this.phoneNumber = phoneNumber;
    	}
    	
    	public UpdatesHandler(String phoneNumber, String code) {
    		this.code = code;
    		this.phoneNumber = phoneNumber;
    	}
    	
    	@Override
        public void onResult(TdApi.Object object) {
            switch (object.getConstructor()) {
                case TdApi.UpdateAuthorizationState.CONSTRUCTOR:
                	onAuthorizationStateUpdated(((TdApi.UpdateAuthorizationState) object).authorizationState, phoneNumber, code);
                    break;
                case TdApi.UpdateUser.CONSTRUCTOR:
                    TdApi.UpdateUser updateUser = (TdApi.UpdateUser) object;
                    users.put(updateUser.user.id, updateUser.user);
                    break;
                case TdApi.UpdateUserStatus.CONSTRUCTOR:  {
                    TdApi.UpdateUserStatus updateUserStatus = (TdApi.UpdateUserStatus) object;
                    TdApi.User user = users.get(updateUserStatus.userId);
                    synchronized (user) {
                        user.status = updateUserStatus.status;
                    }
                    break;
                }
                case TdApi.UpdateBasicGroup.CONSTRUCTOR:
                    TdApi.UpdateBasicGroup updateBasicGroup = (TdApi.UpdateBasicGroup) object;
                    basicGroups.put(updateBasicGroup.basicGroup.id, updateBasicGroup.basicGroup);
                    break;
                case TdApi.UpdateSupergroup.CONSTRUCTOR:
                    TdApi.UpdateSupergroup updateSupergroup = (TdApi.UpdateSupergroup) object;
                    supergroups.put(updateSupergroup.supergroup.id, updateSupergroup.supergroup);
                    break;
                case TdApi.UpdateSecretChat.CONSTRUCTOR:
                    TdApi.UpdateSecretChat updateSecretChat = (TdApi.UpdateSecretChat) object;
                    secretChats.put(updateSecretChat.secretChat.id, updateSecretChat.secretChat);
                    break;

                case TdApi.UpdateNewChat.CONSTRUCTOR: {
                    TdApi.UpdateNewChat updateNewChat = (TdApi.UpdateNewChat) object;
                    TdApi.Chat chat = updateNewChat.chat;
                    synchronized (chat) {
                        chats.put(chat.id, chat);

                        long order = chat.order;
                        chat.order = 0;
                        setChatOrder(chat, order);
                    }
                    break;
                }
                case TdApi.UpdateChatTitle.CONSTRUCTOR: {
                    TdApi.UpdateChatTitle updateChat = (TdApi.UpdateChatTitle) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.title = updateChat.title;
                    }
                    break;
                }
                case TdApi.UpdateChatPhoto.CONSTRUCTOR: {
                    TdApi.UpdateChatPhoto updateChat = (TdApi.UpdateChatPhoto) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.photo = updateChat.photo;
                    }
                    break;
                }
                case TdApi.UpdateChatChatList.CONSTRUCTOR: {
                    TdApi.UpdateChatChatList updateChat = (TdApi.UpdateChatChatList) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (mainChatList) { // to not change Chat.chatList while mainChatList is locked
                        synchronized (chat) {
                            assert chat.order == 0; // guaranteed by TDLib
                            chat.chatList = updateChat.chatList;
                        }
                    }
                    break;
                }
                case TdApi.UpdateChatLastMessage.CONSTRUCTOR: {
                    TdApi.UpdateChatLastMessage updateChat = (TdApi.UpdateChatLastMessage) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.lastMessage = updateChat.lastMessage;
                        setChatOrder(chat, updateChat.order);
                    }
                    break;
                }
                case TdApi.UpdateChatOrder.CONSTRUCTOR: {
                    TdApi.UpdateChatOrder updateChat = (TdApi.UpdateChatOrder) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        setChatOrder(chat, updateChat.order);
                    }
                    break;
                }
                case TdApi.UpdateChatIsPinned.CONSTRUCTOR: {
                    TdApi.UpdateChatIsPinned updateChat = (TdApi.UpdateChatIsPinned) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.isPinned = updateChat.isPinned;
                        setChatOrder(chat, updateChat.order);
                    }
                    break;
                }
                case TdApi.UpdateChatReadInbox.CONSTRUCTOR: {
                    TdApi.UpdateChatReadInbox updateChat = (TdApi.UpdateChatReadInbox) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.lastReadInboxMessageId = updateChat.lastReadInboxMessageId;
                        chat.unreadCount = updateChat.unreadCount;
                    }
                    break;
                }
                case TdApi.UpdateChatReadOutbox.CONSTRUCTOR: {
                    TdApi.UpdateChatReadOutbox updateChat = (TdApi.UpdateChatReadOutbox) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.lastReadOutboxMessageId = updateChat.lastReadOutboxMessageId;
                    }
                    break;
                }
                case TdApi.UpdateChatUnreadMentionCount.CONSTRUCTOR: {
                    TdApi.UpdateChatUnreadMentionCount updateChat = (TdApi.UpdateChatUnreadMentionCount) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.unreadMentionCount = updateChat.unreadMentionCount;
                    }
                    break;
                }
                case TdApi.UpdateMessageMentionRead.CONSTRUCTOR: {
                    TdApi.UpdateMessageMentionRead updateChat = (TdApi.UpdateMessageMentionRead) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.unreadMentionCount = updateChat.unreadMentionCount;
                    }
                    break;
                }
                case TdApi.UpdateChatReplyMarkup.CONSTRUCTOR: {
                    TdApi.UpdateChatReplyMarkup updateChat = (TdApi.UpdateChatReplyMarkup) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.replyMarkupMessageId = updateChat.replyMarkupMessageId;
                    }
                    break;
                }
                case TdApi.UpdateChatDraftMessage.CONSTRUCTOR: {
                    TdApi.UpdateChatDraftMessage updateChat = (TdApi.UpdateChatDraftMessage) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.draftMessage = updateChat.draftMessage;
                        setChatOrder(chat, updateChat.order);
                    }
                    break;
                }
                case TdApi.UpdateChatNotificationSettings.CONSTRUCTOR: {
                    TdApi.UpdateChatNotificationSettings update = (TdApi.UpdateChatNotificationSettings) object;
                    TdApi.Chat chat = chats.get(update.chatId);
                    synchronized (chat) {
                        chat.notificationSettings = update.notificationSettings;
                    }
                    break;
                }
                case TdApi.UpdateChatDefaultDisableNotification.CONSTRUCTOR: {
                    TdApi.UpdateChatDefaultDisableNotification update = (TdApi.UpdateChatDefaultDisableNotification) object;
                    TdApi.Chat chat = chats.get(update.chatId);
                    synchronized (chat) {
                        chat.defaultDisableNotification = update.defaultDisableNotification;
                    }
                    break;
                }
                case TdApi.UpdateChatIsMarkedAsUnread.CONSTRUCTOR: {
                    TdApi.UpdateChatIsMarkedAsUnread update = (TdApi.UpdateChatIsMarkedAsUnread) object;
                    TdApi.Chat chat = chats.get(update.chatId);
                    synchronized (chat) {
                        chat.isMarkedAsUnread = update.isMarkedAsUnread;
                    }
                    break;
                }
                case TdApi.UpdateChatIsSponsored.CONSTRUCTOR: {
                    TdApi.UpdateChatIsSponsored updateChat = (TdApi.UpdateChatIsSponsored) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.isSponsored = updateChat.isSponsored;
                        setChatOrder(chat, updateChat.order);
                    }
                    break;
                }

                case TdApi.UpdateUserFullInfo.CONSTRUCTOR:
                    TdApi.UpdateUserFullInfo updateUserFullInfo = (TdApi.UpdateUserFullInfo) object;
                    usersFullInfo.put(updateUserFullInfo.userId, updateUserFullInfo.userFullInfo);
                    break;
                case TdApi.UpdateBasicGroupFullInfo.CONSTRUCTOR:
                    TdApi.UpdateBasicGroupFullInfo updateBasicGroupFullInfo = (TdApi.UpdateBasicGroupFullInfo) object;
                    basicGroupsFullInfo.put(updateBasicGroupFullInfo.basicGroupId, updateBasicGroupFullInfo.basicGroupFullInfo);
                    break;
                case TdApi.UpdateSupergroupFullInfo.CONSTRUCTOR:
                    TdApi.UpdateSupergroupFullInfo updateSupergroupFullInfo = (TdApi.UpdateSupergroupFullInfo) object;
                    supergroupsFullInfo.put(updateSupergroupFullInfo.supergroupId, updateSupergroupFullInfo.supergroupFullInfo);
                    break;
                default:
                    // print("Unsupported update:" + newLine + object);
            }
        }
    }

    private static class AuthorizationRequestHandler implements Client.ResultHandler {
    	
    	private String phoneNumber;
    	
    	private String code;
    	
    	public AuthorizationRequestHandler(String phoneNumber) {
    		this.phoneNumber = phoneNumber;
    	}
    	
    	public void setCode(String code) {
    		this.code = code;
    	}
    	
        @Override
        public void onResult(TdApi.Object object) {
            switch (object.getConstructor()) {
                case TdApi.Error.CONSTRUCTOR:
                    System.err.println("Receive an error:" + newLine + object);
                    onAuthorizationStateUpdated(null, phoneNumber, code); // repeat last action
                    break;
                case TdApi.Ok.CONSTRUCTOR:
                    // result is already received through UpdateAuthorizationState, nothing to do
                    break;
                default:
                    System.err.println("Receive wrong response from TDLib:" + newLine + object);
            }
        }
    }

    private static class ExceptionHandler implements Client.ExceptionHandler {

		@Override
		public void onException(Throwable arg0) {
			print(arg0.getMessage());
			arg0.printStackTrace();
		}
    }
    
    private static class OrderedChat implements Comparable<OrderedChat> {
        final long order;
        final long chatId;

        OrderedChat(long order, long chatId) {
            this.order = order;
            this.chatId = chatId;
        }

        @Override
        public int compareTo(OrderedChat o) {
            if (this.order != o.order) {
                return o.order < this.order ? -1 : 1;
            }
            if (this.chatId != o.chatId) {
                return o.chatId < this.chatId ? -1 : 1;
            }
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            OrderedChat o = (OrderedChat) obj;
            return this.order == o.order && this.chatId == o.chatId;
        }
    }
}
