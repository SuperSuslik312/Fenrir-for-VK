package dev.ragnarok.fenrir.mvp.presenter;

import static dev.ragnarok.fenrir.util.Objects.nonNull;
import static dev.ragnarok.fenrir.util.Utils.listEmptyIfNull;
import static dev.ragnarok.fenrir.util.Utils.nonEmpty;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import dev.ragnarok.fenrir.Injection;
import dev.ragnarok.fenrir.R;
import dev.ragnarok.fenrir.api.model.VKApiCommunity;
import dev.ragnarok.fenrir.api.model.VKApiUser;
import dev.ragnarok.fenrir.db.column.UserColumns;
import dev.ragnarok.fenrir.domain.IGroupSettingsInteractor;
import dev.ragnarok.fenrir.domain.Repository;
import dev.ragnarok.fenrir.domain.impl.GroupSettingsInteractor;
import dev.ragnarok.fenrir.domain.mappers.Dto2Model;
import dev.ragnarok.fenrir.model.Community;
import dev.ragnarok.fenrir.model.ContactInfo;
import dev.ragnarok.fenrir.model.Manager;
import dev.ragnarok.fenrir.model.Owner;
import dev.ragnarok.fenrir.model.User;
import dev.ragnarok.fenrir.mvp.presenter.base.AccountDependencyPresenter;
import dev.ragnarok.fenrir.mvp.view.ICommunityManagersView;
import dev.ragnarok.fenrir.util.Analytics;
import dev.ragnarok.fenrir.util.RxUtils;
import dev.ragnarok.fenrir.util.Utils;


public class CommunityManagersPresenter extends AccountDependencyPresenter<ICommunityManagersView> {

    private final Community groupId;

    private final List<Manager> data;

    private final IGroupSettingsInteractor interactor;
    private boolean loadingNow;

    public CommunityManagersPresenter(int accountId, Community groupId, @Nullable Bundle savedInstanceState) {
        super(accountId, savedInstanceState);
        interactor = new GroupSettingsInteractor(Injection.provideNetworkInterfaces(), Injection.provideStores().owners(), Repository.INSTANCE.getOwners());
        this.groupId = groupId;
        data = new ArrayList<>();

        appendDisposable(Injection.provideStores()
                .owners()
                .observeManagementChanges()
                .filter(pair -> pair.getFirst() == groupId.getId())
                .observeOn(Injection.provideMainThreadScheduler())
                .subscribe(pair -> onManagerActionReceived(pair.getSecond()), Analytics::logUnexpectedError));

        requestData();
    }

    private void onManagerActionReceived(Manager manager) {
        int index = Utils.findIndexByPredicate(data, m -> m.getUser().getId() == manager.getUser().getId());
        boolean removing = Utils.isEmpty(manager.getRole());

        if (index != -1) {
            if (removing) {
                data.remove(index);
                callView(view -> view.notifyItemRemoved(index));
            } else {
                data.set(index, manager);
                callView(view -> view.notifyItemChanged(index));
            }
        } else {
            if (!removing) {
                data.add(0, manager);
                callView(view -> view.notifyItemAdded(0));
            }
        }
    }

    private ContactInfo findByIdU(List<ContactInfo> contacts, int user_id) {
        for (ContactInfo element : contacts) {
            if (element.getUserId() == user_id) {
                return element;
            }
        }
        return null;
    }

    private void onContactsReceived(List<ContactInfo> contacts) {
        int accountId = getAccountId();
        List<Integer> Ids = new ArrayList<>(contacts.size());
        for (ContactInfo it : contacts)
            Ids.add(it.getUserId());
        appendDisposable(Injection.provideNetworkInterfaces().vkDefault(accountId).users().get(Ids, null, UserColumns.API_FIELDS, null)
                .compose(RxUtils.applySingleIOToMainSchedulers())
                .subscribe(t -> {
                    List<VKApiUser> users = listEmptyIfNull(t);
                    List<Manager> managers = new ArrayList<>(users.size());
                    for (VKApiUser user : users) {
                        ContactInfo contact = findByIdU(contacts, user.id);
                        Manager manager = new Manager(Dto2Model.transformUser(user), user.role);
                        if (nonNull(contact)) {
                            manager.setDisplayAsContact(true).setContactInfo(contact);
                        }
                        managers.add(manager);
                        onDataReceived(managers);
                    }
                }, this::onRequestError));
    }

    private void requestContacts() {
        int accountId = getAccountId();
        appendDisposable(interactor.getContacts(accountId, groupId.getId())
                .compose(RxUtils.applySingleIOToMainSchedulers())
                .subscribe(this::onContactsReceived, this::onRequestError));
    }

    private void requestData() {
        int accountId = getAccountId();

        setLoadingNow(true);
        if (groupId.getAdminLevel() < VKApiCommunity.AdminLevel.ADMIN) {
            requestContacts();
            return;
        }
        appendDisposable(interactor.getManagers(accountId, groupId.getId())
                .compose(RxUtils.applySingleIOToMainSchedulers())
                .subscribe(this::onDataReceived, this::onRequestError));
    }

    @Override
    public void onGuiCreated(@NonNull ICommunityManagersView view) {
        super.onGuiCreated(view);
        view.displayData(data);
    }

    private void setLoadingNow(boolean loadingNow) {
        this.loadingNow = loadingNow;
        resolveRefreshingView();
    }

    @Override
    public void onGuiResumed() {
        super.onGuiResumed();
        resolveRefreshingView();
    }

    private void resolveRefreshingView() {
        callResumedView(v -> v.displayRefreshing(loadingNow));
    }

    private void onRequestError(Throwable throwable) {
        setLoadingNow(false);
        callView(v -> showError(v, throwable));
    }

    private void onDataReceived(List<Manager> managers) {
        setLoadingNow(false);

        data.clear();
        data.addAll(managers);

        callView(ICommunityManagersView::notifyDataSetChanged);
    }

    public void fireRefresh() {
        requestData();
    }

    public void fireManagerClick(Manager manager) {
        callView(v -> v.goToManagerEditing(getAccountId(), groupId.getId(), manager));
    }

    public void fireRemoveClick(Manager manager) {
        int accountId = getAccountId();
        User user = manager.getUser();

        appendDisposable(interactor.editManager(accountId, groupId.getId(), user, null, false, null, null, null)
                .compose(RxUtils.applyCompletableIOToMainSchedulers())
                .subscribe(this::onRemoveComplete, throwable -> onRemoveError(Utils.getCauseIfRuntime(throwable))));
    }

    private void onRemoveError(Throwable throwable) {
        throwable.printStackTrace();
        callView(v -> showError(v, throwable));
    }

    private void onRemoveComplete() {
        callView(v -> v.showToast(R.string.deleted, false));
    }

    public void fireButtonAddClick() {
        callView(v -> v.startSelectProfilesActivity(getAccountId(), groupId.getId()));
    }

    public void fireProfilesSelected(ArrayList<Owner> owners) {
        ArrayList<User> users = new ArrayList<>();
        for (Owner i : owners) {
            if (i instanceof User) {
                users.add((User) i);
            }
        }
        if (nonEmpty(users)) {
            callView(v -> v.startAddingUsersToManagers(getAccountId(), groupId.getId(), users));
        }
    }
}