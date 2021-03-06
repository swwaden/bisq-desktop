/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.dao.proposal.active;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.dao.proposal.BaseProposalView;
import bisq.desktop.main.dao.proposal.ProposalListItem;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.Layout;

import bisq.core.btc.wallet.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoPeriodService;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.BsqBlockChainChangeDispatcher;
import bisq.core.dao.proposal.Proposal;
import bisq.core.dao.proposal.ProposalCollectionsManager;
import bisq.core.dao.vote.BooleanVoteResult;
import bisq.core.dao.vote.VoteManager;
import bisq.core.locale.Res;

import bisq.common.util.Tuple3;

import javax.inject.Inject;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.util.Callback;

import java.util.Comparator;

import static bisq.desktop.util.FormBuilder.add3ButtonsAfterGroup;
import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addLabelInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class ActiveProposalsView extends BaseProposalView {

    private final VoteManager voteManager;

    private Button removeButton, acceptButton, rejectButton, cancelVoteButton, voteButton;
    private InputTextField stakeInputTextField;
    private BsqBalanceListener bsqBalanceListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ActiveProposalsView(ProposalCollectionsManager voteRequestManger,
                                DaoPeriodService daoPeriodService,
                                VoteManager voteManager,
                                BsqWalletService bsqWalletService,
                                BsqBlockChain bsqBlockChain,
                                BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher,
                                BsqFormatter bsqFormatter) {
        super(voteRequestManger, bsqWalletService, bsqBlockChain, bsqBlockChainChangeDispatcher, daoPeriodService,
                bsqFormatter);
        this.voteManager = voteManager;
    }

    @Override
    public void initialize() {
        super.initialize();

        createTableView();

        addTitledGroupBg(root, ++gridRow, 1, Res.get("dao.proposal.vote.header"), Layout.GROUP_DISTANCE - 20);
        stakeInputTextField = addLabelInputTextField(root, gridRow, Res.getWithCol("dao.proposal.vote.stake"), Layout
                .FIRST_ROW_AND_GROUP_DISTANCE - 20).second;
        voteButton = addButtonAfterGroup(root, ++gridRow, Res.get("dao.proposal.vote.button"));

        createProposalDisplay();

        bsqBalanceListener = (availableBalance, unverifiedBalance) -> stakeInputTextField.setPromptText(Res.get("dao.proposal.vote.stake.prompt", bsqFormatter.formatCoinWithCode(availableBalance)));
    }

    @Override
    protected void activate() {
        super.activate();

        bsqWalletService.addBsqBalanceListener(bsqBalanceListener);
        stakeInputTextField.setPromptText(Res.get("dao.proposal.vote.stake.prompt", bsqFormatter.formatCoinWithCode
                (bsqWalletService.getAvailableBalance())));

        voteButton.setOnAction(e -> {
            voteManager.vote();
        });
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        bsqWalletService.removeBsqBalanceListener(bsqBalanceListener);
        voteButton.setOnAction(null);
    }

    @Override
    protected void updateList() {
        doUpdateList(proposalCollectionsManager.getActiveProposals());
    }

    protected void onSelectProposal(ProposalListItem item) {
        super.onSelectProposal(item);
        if (item != null) {
            if (removeButton != null) {
                removeButton.setManaged(false);
                removeButton.setVisible(false);
                removeButton = null;
            }
            if (acceptButton != null) {
                acceptButton.setManaged(false);
                acceptButton.setVisible(false);
                acceptButton = null;
            }
            if (rejectButton != null) {
                rejectButton.setManaged(false);
                rejectButton.setVisible(false);
                rejectButton = null;
            }
            if (cancelVoteButton != null) {
                cancelVoteButton.setManaged(false);
                cancelVoteButton.setVisible(false);
                cancelVoteButton = null;
            }

            onPhaseChanged(daoPeriodService.getPhaseProperty().get());
        }
    }

    private void onAccept() {
        selectedProposalListItem.getProposal().setVoteResult(new BooleanVoteResult(true));
        updateStateAfterVote();
    }

    private void onReject() {
        selectedProposalListItem.getProposal().setVoteResult(new BooleanVoteResult(false));
        updateStateAfterVote();
    }

    private void onCancelVote() {
        selectedProposalListItem.getProposal().setVoteResult(null);
        updateStateAfterVote();
    }

    private void updateStateAfterVote() {
        removeProposalDisplay();
        proposalCollectionsManager.queueUpForSave();
        tableView.getSelectionModel().clearSelection();
    }

    private void onRemove() {
        if (proposalCollectionsManager.removeProposal(selectedProposalListItem.getProposal()))
            removeProposalDisplay();
        else
            new Popup<>().warning(Res.get("dao.proposal.active.remove.failed")).show();

        tableView.getSelectionModel().clearSelection();
    }

    @Override
    protected void onPhaseChanged(DaoPeriodService.Phase phase) {
        if (removeButton != null) {
            removeButton.setManaged(false);
            removeButton.setVisible(false);
            removeButton = null;
        }
        if (selectedProposalListItem != null && proposalDisplay != null && !selectedProposalListItem.getProposal().isClosed()) {
            final Proposal proposal = selectedProposalListItem.getProposal();
            switch (phase) {
                case COMPENSATION_REQUESTS:
                    if (proposalCollectionsManager.isMine(proposal)) {
                        if (removeButton == null) {
                            removeButton = addButtonAfterGroup(detailsGridPane, proposalDisplay.incrementAndGetGridRow(), Res.get("dao.proposal.active.remove"));
                            removeButton.setOnAction(event -> onRemove());
                        } else {
                            removeButton.setManaged(true);
                            removeButton.setVisible(true);
                        }
                    }
                    break;
                case BREAK1:
                    break;
                case OPEN_FOR_VOTING:
                    if (acceptButton == null) {
                        Tuple3<Button, Button, Button> tuple = add3ButtonsAfterGroup(detailsGridPane, proposalDisplay
                                        .incrementAndGetGridRow(),
                                Res.get("dao.proposal.vote.accept"),
                                Res.get("dao.proposal.vote.reject"),
                                Res.get("dao.proposal.vote.cancelVote"));
                        acceptButton = tuple.first;
                        acceptButton.setDefaultButton(false);
                        rejectButton = tuple.second;
                        cancelVoteButton = tuple.third;
                        acceptButton.setOnAction(event -> onAccept());
                        rejectButton.setOnAction(event -> onReject());
                        cancelVoteButton.setOnAction(event -> onCancelVote());
                    } else {
                        acceptButton.setManaged(true);
                        acceptButton.setVisible(true);
                        rejectButton.setManaged(true);
                        rejectButton.setVisible(true);
                        cancelVoteButton.setManaged(true);
                        cancelVoteButton.setVisible(true);
                    }
                    break;
                case BREAK2:
                    break;
                case VOTE_REVEAL:
                    break;
                case BREAK3:
                    break;
                case UNDEFINED:
                default:
                    log.warn("Undefined phase: " + phase);
                    break;
            }
        }
    }

    @Override
    protected void createColumns(TableView<ProposalListItem> tableView) {
        super.createColumns(tableView);

        TableColumn<ProposalListItem, ProposalListItem> actionColumn = new TableColumn<>();
        actionColumn.setMinWidth(130);
        actionColumn.setMaxWidth(actionColumn.getMinWidth());

        actionColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        actionColumn.setCellFactory(new Callback<TableColumn<ProposalListItem, ProposalListItem>,
                TableCell<ProposalListItem, ProposalListItem>>() {

            @Override
            public TableCell<ProposalListItem, ProposalListItem> call(TableColumn<ProposalListItem,
                    ProposalListItem> column) {
                return new TableCell<ProposalListItem, ProposalListItem>() {
                    Node node;

                    @Override
                    public void updateItem(final ProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            if (node == null) {
                                node = item.getActionNode();
                                setGraphic(node);
                                item.setOnRemoveHandler(ActiveProposalsView.this::onRemove);
                            }
                        } else {
                            setGraphic(null);
                            if (node != null) {
                                if (node instanceof Button)
                                    ((Button) node).setOnAction(null);
                                node = null;
                            }
                        }
                    }
                };
            }
        });
        actionColumn.setComparator(Comparator.comparing(ProposalListItem::getConfirmations));
        tableView.getColumns().add(actionColumn);
    }
}

