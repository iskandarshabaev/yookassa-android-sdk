/*
 * The MIT License (MIT)
 * Copyright © 2018 NBCO Yandex.Money LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the “Software”), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do so, subject to the
 * following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
 * OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package ru.yandex.money.android.sdk.impl.contract

import android.content.Context
import android.content.Intent
import android.support.v4.content.ContextCompat
import android.support.v4.content.ContextCompat.startActivity
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import ru.yandex.money.android.sdk.PaymentMethodType
import ru.yandex.money.android.sdk.R
import ru.yandex.money.android.sdk.impl.extensions.toHint
import ru.yandex.money.android.sdk.impl.payment.PaymentOptionPresenter
import ru.yandex.money.android.sdk.impl.paymentAuth.ProcessPaymentAuthProgressViewModel
import ru.yandex.money.android.sdk.impl.paymentAuth.RequestPaymentAuthProgressViewModel
import ru.yandex.money.android.sdk.impl.paymentAuth.SmsSessionRetryProgressViewModel
import ru.yandex.money.android.sdk.model.AuthType
import ru.yandex.money.android.sdk.model.GooglePay
import ru.yandex.money.android.sdk.model.NewCard
import ru.yandex.money.android.sdk.model.SbolSmsInvoicing
import ru.yandex.money.android.sdk.model.YandexMoney
import ru.yandex.money.android.sdk.payment.selectOption.SelectPaymentOptionOutputModel
import ru.yandex.money.android.sdk.payment.selectOption.SelectedPaymentOptionOutputModel
import ru.yandex.money.android.sdk.payment.selectOption.UserAuthRequired
import ru.yandex.money.android.sdk.payment.tokenize.TokenOutputModel
import ru.yandex.money.android.sdk.payment.tokenize.TokenizeOutputModel
import ru.yandex.money.android.sdk.payment.tokenize.TokenizePaymentAuthRequiredOutputModel
import ru.yandex.money.android.sdk.paymentAuth.ProcessPaymentAuthOutputModel
import ru.yandex.money.android.sdk.paymentAuth.ProcessPaymentAuthSuccessOutputModel
import ru.yandex.money.android.sdk.paymentAuth.ProcessPaymentAuthWrongAnswerOutputModel
import ru.yandex.money.android.sdk.paymentAuth.RequestPaymentAuthOutputModel
import ru.yandex.money.android.sdk.paymentAuth.SmsSessionRetryOutputModel
import ru.yandex.money.android.sdk.utils.WebViewActivity
import java.math.BigDecimal

internal class ContractPresenter(
    context: Context,
    private val shopTitle: CharSequence,
    private val shopSubtitle: CharSequence,
    private val recurringPaymentsPossible: Boolean
) {
    private val context = context.applicationContext
    private val paymentOptionPresenter = PaymentOptionPresenter(context.applicationContext)

    private lateinit var contract: ContractSuccessViewModel
    private lateinit var paymentAuthForm: PaymentAuthFormViewModel

    operator fun invoke(model: SelectPaymentOptionOutputModel): ContractViewModel {
        return when (model) {
            is SelectedPaymentOptionOutputModel -> {
                when {
                    model.paymentOption is GooglePay -> {
                        val fee = model.paymentOption.fee?.service?.value
                        if (fee == null || fee == BigDecimal.ZERO) {
                            GooglePayContractViewModel(model.paymentOption.id, recurringPaymentsPossible)
                        } else {
                            ContractSuccessViewModel(
                                shopTitle = shopTitle,
                                shopSubtitle = shopSubtitle,
                                licenseAgreement = getLicenseAgreementText(),
                                paymentOption = paymentOptionPresenter(model.paymentOption),
                                showChangeButton = model.hasAnotherOptions,
                                showAllowRecurringPayments = recurringPaymentsPossible,
                                showAllowWalletLinking = model.walletLinkingPossible,
                                paymentAuth = null,
                                showPhoneInput = model.paymentOption is SbolSmsInvoicing,
                                googlePayContractViewModel = GooglePayContractViewModel(
                                    model.paymentOption.id,
                                    recurringPaymentsPossible
                                )
                            ).also { contract = it }
                        }
                    }
                    else -> ContractSuccessViewModel(
                        shopTitle = shopTitle,
                        shopSubtitle = shopSubtitle,
                        licenseAgreement = getLicenseAgreementText(),
                        paymentOption = paymentOptionPresenter(model.paymentOption),
                        showChangeButton = model.hasAnotherOptions,
                        showAllowRecurringPayments = recurringPaymentsPossible,
                        showAllowWalletLinking = model.walletLinkingPossible,
                        paymentAuth = null,
                        showPhoneInput = model.paymentOption is SbolSmsInvoicing
                    ).also { contract = it }
                }
            }
            is UserAuthRequired -> ContractUserAuthRequiredViewModel
        }
    }

    private fun getLicenseAgreementText(): CharSequence {
        val link = context.getText(R.string.ym_license_agreement_part_2)

        return SpannableStringBuilder(
            "${context.getText(R.string.ym_license_agreement_part_1)} " + "$link"
        ).apply {
            setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        startActivity(
                            context,
                            WebViewActivity.create(
                                context,
                                context.getString(R.string.ym_license_agreement_url)
                            ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            null
                        )
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.apply {
                            color = ContextCompat.getColor(context, R.color.ym_button_text_link)
                            isUnderlineText = false
                        }
                    }
                },
                length - link.length,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    operator fun invoke(model: TokenizeOutputModel) = when (model) {
        is TokenOutputModel -> ContractCompleteViewModel(
            token = model.token,
            type = when (model.option) {
                is YandexMoney -> PaymentMethodType.YANDEX_MONEY
                is NewCard -> PaymentMethodType.BANK_CARD
                is GooglePay -> PaymentMethodType.GOOGLE_PAY
                is SbolSmsInvoicing -> PaymentMethodType.SBERBANK
            }
        )
        is TokenizePaymentAuthRequiredOutputModel -> contract.copy(
            showAllowRecurringPayments = false,
            showAllowWalletLinking = false,
            paymentAuth = PaymentAuthStartViewModel(model.charge)
        )
        else -> throw IllegalArgumentException("model not allowed: $model")
    }

    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(model: RequestPaymentAuthProgressViewModel) = contract.copy(
        showAllowRecurringPayments = false,
        showAllowWalletLinking = false,
        paymentAuth = PaymentAuthProgressViewModel()
    )

    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(model: ProcessPaymentAuthProgressViewModel) = contract.copy(
        showAllowRecurringPayments = false,
        showAllowWalletLinking = false,
        paymentAuth = PaymentAuthProgressViewModel()
    )

    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(model: SmsSessionRetryProgressViewModel) = contract.copy(
        showAllowRecurringPayments = false,
        showAllowWalletLinking = false,
        paymentAuth = PaymentAuthProgressViewModel()
    )

    operator fun invoke(model: RequestPaymentAuthOutputModel) = contract.copy(
        showAllowRecurringPayments = false,
        showAllowWalletLinking = false,
        paymentAuth = when (model.authTypeState.type) {
            AuthType.SMS, AuthType.PUSH -> PaymentAuthFormRetryViewModel(
                hint = model.authTypeState.type.toHint(context),
                timeout = model.authTypeState.nextSessionTimeLeft,
                error = null
            )
            else -> PaymentAuthFormNoRetryViewModel(
                hint = model.authTypeState.type.toHint(context),
                error = null
            )
        }.also { paymentAuthForm = it }
    )

    operator fun invoke(model: ProcessPaymentAuthOutputModel) = contract.copy(
        showAllowRecurringPayments = false,
        showAllowWalletLinking = false,
        paymentAuth = when (model) {
            is ProcessPaymentAuthSuccessOutputModel ->
                PaymentAuthSuccessViewModel()
            is ProcessPaymentAuthWrongAnswerOutputModel ->
                when (paymentAuthForm) {
                    is PaymentAuthFormRetryViewModel -> (paymentAuthForm as PaymentAuthFormRetryViewModel).copy(
                        error = context.getText(R.string.ym_wrong_passcode_error)
                    )
                    is PaymentAuthFormNoRetryViewModel -> (paymentAuthForm as PaymentAuthFormNoRetryViewModel).copy(
                        error = context.getText(R.string.ym_wrong_passcode_error)
                    )
                }
        }
    )

    operator fun invoke(model: SmsSessionRetryOutputModel) = contract.copy(
        showAllowRecurringPayments = false,
        showAllowWalletLinking = false,
        paymentAuth = PaymentAuthFormRetryViewModel(
            hint = model.authTypeState.type.toHint(context),
            timeout = model.authTypeState.nextSessionTimeLeft,
            error = null
        ).also { paymentAuthForm = it }
    )
}
