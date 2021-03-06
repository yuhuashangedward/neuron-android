package org.nervos.neuron.activity.transactionlist.presenter;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import org.nervos.neuron.R;
import org.nervos.neuron.activity.transactionlist.model.TokenDescribeModel;
import org.nervos.neuron.item.ChainItem;
import org.nervos.neuron.item.EthErc20TokenInfoItem;
import org.nervos.neuron.item.TokenItem;
import org.nervos.neuron.item.TransactionItem;
import org.nervos.neuron.service.httpservice.AppChainTransactionService;
import org.nervos.neuron.service.httpservice.HttpService;
import org.nervos.neuron.service.httpservice.HttpUrls;
import org.nervos.neuron.service.httpservice.TokenService;
import org.nervos.neuron.util.AddressUtil;
import org.nervos.neuron.util.CurrencyUtil;
import org.nervos.neuron.util.db.DBAppChainTransactionsUtil.TokenType;
import org.nervos.neuron.util.db.DBChainUtil;
import org.web3j.crypto.Keys;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;

import rx.Observable;
import rx.Subscriber;

/**
 * Created by BaojunCZ on 2018/10/9.
 */
public class TransactionListPresenter {

    private Activity activity;
    private TransactionListPresenterImpl listener;
    private TokenDescribeModel tokenDescribeModel;
    private TokenItem tokenItem;

    public TransactionListPresenter(Activity activity, TokenItem tokenItem, TransactionListPresenterImpl listener) {
        this.activity = activity;
        this.listener = listener;
        this.tokenItem = tokenItem;
    }

    public void setTokenLogo(ImageView tokenLogoImage) {
        if (isEthereum(tokenItem)) {
            if (!isNativeToken(tokenItem)) {
                if (TextUtils.isEmpty(tokenItem.avatar)) {
                    String address = tokenItem.contractAddress;
                    if (AddressUtil.isAddressValid(address))
                        address = Keys.toChecksumAddress(address);
                    RequestOptions options = new RequestOptions()
                            .error(R.drawable.ether_big);
                    Glide.with(activity)
                            .load(Uri.parse(HttpUrls.TOKEN_LOGO.replace("@address", address)))
                            .apply(options)
                            .into(tokenLogoImage);
                } else
                    Glide.with(activity)
                            .load(Uri.parse(tokenItem.avatar))
                            .into(tokenLogoImage);
            }
        }
    }

    public void getTokenDescribe() {
        tokenDescribeModel = new TokenDescribeModel(new TokenDescribeModel.TokenDescribeModelImpl() {
            @Override
            public void success(EthErc20TokenInfoItem item) {
                listener.getTokenDescribe(item);
                listener.showTokenDescribe(true);
            }

            @Override
            public void error() {
            }
        });
        tokenDescribeModel.get(tokenItem.contractAddress);
    }

    public void getBalance() {
        if (tokenItem.balance > 0.0 && isEthereum(tokenItem)) {
            TokenService.getCurrency(tokenItem.symbol, CurrencyUtil.getCurrencyItem(activity).getName())
                    .subscribe(new Subscriber<String>() {
                        @Override
                        public void onCompleted() {
                        }

                        @Override
                        public void onError(Throwable e) {
                            e.printStackTrace();
                        }

                        @Override
                        public void onNext(String s) {
                            if (!TextUtils.isEmpty(s)) {
                                double price = Double.parseDouble(s.trim());
                                DecimalFormat df = new DecimalFormat("######0.00");
                                DecimalFormat format = new DecimalFormat("0.####");
                                format.setRoundingMode(RoundingMode.FLOOR);
                                listener.getCurrency(CurrencyUtil.getCurrencyItem(activity).getSymbol()
                                        + Double.parseDouble(df.format(price * tokenItem.balance)));
                            } else
                                listener.getCurrency("0");
                        }
                    });
        }
    }

    public void getTransactionList(String from) {
        Observable<List<TransactionItem>> observable;
        if (isNativeToken(tokenItem)) {
            if (tokenItem.chainId != 1) {
                getUnofficialNoneData();       // Now only support chainId = 1 (225 NATT)
                return;
            }
            observable = isEthereum(tokenItem) ?
                    HttpService.getETHTransactionList(activity) :
                    HttpService.getAppChainTransactionList(activity);
        } else {
            observable = isEthereum(tokenItem) ?
                    HttpService.getETHERC20TransactionList(activity, tokenItem) :
                    HttpService.getAppChainERC20TransactionList(activity, tokenItem);
        }
        observable.subscribe(new Subscriber<List<TransactionItem>>() {
            @Override
            public void onCompleted() {
                listener.hideProgressBar();
                listener.setRefreshing(false);
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
                listener.hideProgressBar();
                listener.setRefreshing(false);
            }

            @Override
            public void onNext(List<TransactionItem> list) {
                if (list == null) {
                    Toast.makeText(activity, R.string.network_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                Collections.sort(list, (o1, o2) -> o2.getDate().compareTo(o1.getDate()));
                if (isEthereum(tokenItem)) {
                    for (TransactionItem item : list) {
                        item.status = TransactionItem.SUCCESS;
                    }
                    listener.refreshList(list);
                } else {
                    for (TransactionItem item : list) {
                        item.status = TextUtils.isEmpty(item.errorMessage) ? TransactionItem.SUCCESS : TransactionItem.FAILED;
                    }
                    ChainItem chain = DBChainUtil.getChain(activity, tokenItem.chainId);
                    TokenType tokenType = isNativeToken(tokenItem)? TokenType.NATIVE : TokenType.TOKEN;
                    List<TransactionItem> allList = AppChainTransactionService.getTransactionList(activity, tokenType,
                            chain.httpProvider, tokenItem.contractAddress, list, from);
                    listener.refreshList(allList);
                }
            }
        });
    }

    private void getUnofficialNoneData() {
        listener.hideProgressBar();
        listener.setRefreshing(false);
    }

    public boolean isNativeToken(TokenItem tokenItem) {
        return TextUtils.isEmpty(tokenItem.contractAddress);
    }

    public boolean isEthereum(TokenItem tokenItem) {
        return tokenItem.chainId < 0;
    }

    public interface TransactionListPresenterImpl {
        void hideProgressBar();

        void setRefreshing(boolean refreshing);

        void refreshList(List<TransactionItem> list);

        void getTokenDescribe(EthErc20TokenInfoItem item);

        void showTokenDescribe(boolean show);

        void getCurrency(String currency);
    }

}
