package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
@SchedulableFlow
class CashTransfer2(val stateRef: StateRef) : BaseFlow2() {
    companion object {

        object PREPARATION : ProgressTracker.Step("Fetching State.")
        object BUILDING_TRANSACTION : ProgressTracker.Step("Building Transaction.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying Contract.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing Transaction.")
        object CREATING_SESSION : ProgressTracker.Step("Creating Session for participants.")
        object GATHERING_SIGNATURES : ProgressTracker.Step("Gathering Signatures.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Finalising Transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                PREPARATION,
                BUILDING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                CREATING_SESSION,
                GATHERING_SIGNATURES,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): UniqueIdentifier {
        progressTracker.currentStep = PREPARATION
        val inputStateAndRef = getStateByStateRef(stateRef)
        val inputState = inputStateAndRef.state.data
        val notary = inputStateAndRef.state.notary

        progressTracker.currentStep = BUILDING_TRANSACTION
        val outputState = inputState.withNewOwner(inputState.newOwner).ownableState as CashState

        val signers = outputState.participants.map { abstractParty -> resolveIdentity(abstractParty) }
        val signersKey = signers.map { it.owningKey }

        val command = Command(CashContract.Commands.Pay(), signersKey)
        val transactionBuilder = TransactionBuilder(notary) //.withItems(inputStateAndRef, StateAndContract(outputState, CashContract.CashContractID), command)
        transactionBuilder.addInputState(inputStateAndRef)
                .addOutputState(outputState, CashContract.CashContractID)
                .addCommand(command)

        progressTracker.currentStep = VERIFYING_TRANSACTION
        transactionBuilder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        progressTracker.currentStep = CREATING_SESSION
        val participants = signers.minus(ourIdentity)
        val sessions = participants.map { party: Party -> initiateFlow(party) }

//        subFlow(IdentitySyncFlow.Send(sessions, signedTransaction.tx, SYNCING.childProgressTracker()))

        progressTracker.currentStep = GATHERING_SIGNATURES
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(signedTransaction, sessions, GATHERING_SIGNATURES.childProgressTracker()))

        progressTracker.currentStep = FINALISING_TRANSACTION
        val finalisedTransaction = subFlow(FinalityFlow(fullySignedTransaction, FINALISING_TRANSACTION.childProgressTracker()))
        return finalisedTransaction.tx.outputsOfType<CashState>().first().linearId
    }
}

@InitiatedBy(CashTransfer2::class)
class CashTransferResponse2(val otherSideSession: FlowSession) : BaseFlow2() {
    companion object {
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing Transaction")
        object TRASFERING_ASSET : ProgressTracker.Step("Transfering assset") {
            override fun childProgressTracker() = AssetTransfer2.tracker()
        }

        fun tracker() = ProgressTracker(
                SIGNING_TRANSACTION,
                TRASFERING_ASSET
        )

    }

    override val progressTracker = tracker()
    lateinit var inputStateRef: StateRef
    @Suspendable
    override fun call(): UniqueIdentifier {
        val inputState: CashState
        val signTransactionFlow = object : SignTransactionFlow(otherSideSession, progressTracker = SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) {

                val input = stx.tx.inputs.single()
                val output = stx.tx.outputs.single().data
                inputStateRef = input
                requireThat {
                    "This must be an CashState state." using (output is CashState)
                }
            }
        }
        val v = progressTracker.currentStepRecursive
        progressTracker.currentStep = SIGNING_TRANSACTION
        val signedTransaction = subFlow(signTransactionFlow)
        progressTracker.currentStep = TRASFERING_ASSET
        inputState = serviceHub.toStateAndRef<CashState>(signedTransaction.inputs.single()).state.data
        subFlow(AssetTransfer2(/*otherSideSession,*/UniqueIdentifier.fromString(inputState.assetlinearID),inputState.owner, TRASFERING_ASSET.childProgressTracker()))

        return signedTransaction.tx.outputsOfType<CashState>().first().linearId
    }
}