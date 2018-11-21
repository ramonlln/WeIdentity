/*
 *       Copyright© (2018) WeBank Co., Ltd.
 *
 *       This file is part of weidentity-java-sdk.
 *
 *       weidentity-java-sdk is free software: you can redistribute it and/or modify
 *       it under the terms of the GNU Lesser General Public License as published by
 *       the Free Software Foundation, either version 3 of the License, or
 *       (at your option) any later version.
 *
 *       weidentity-java-sdk is distributed in the hope that it will be useful,
 *       but WITHOUT ANY WARRANTY; without even the implied warranty of
 *       MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *       GNU Lesser General Public License for more details.
 *
 *       You should have received a copy of the GNU Lesser General Public License
 *       along with weidentity-java-sdk.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.webank.weid.contract.deploy;

import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.webank.weid.constant.WeIdConstant;
import com.webank.weid.contract.AuthorityIssuerController;
import com.webank.weid.contract.AuthorityIssuerData;
import com.webank.weid.contract.CommitteeMemberController;
import com.webank.weid.contract.CommitteeMemberData;
import com.webank.weid.contract.CptController;
import com.webank.weid.contract.CptData;
import com.webank.weid.contract.RoleController;
import com.webank.weid.contract.WeIdContract;

import org.apache.commons.lang3.StringUtils;
import org.bcos.channel.client.Service;
import org.bcos.contract.tools.ToolConf;
import org.bcos.web3j.abi.datatypes.Address;
import org.bcos.web3j.crypto.Credentials;
import org.bcos.web3j.crypto.GenCredential;
import org.bcos.web3j.protocol.Web3j;
import org.bcos.web3j.protocol.channel.ChannelEthereumService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * The Class DeployContract.
 *
 * @author tonychen
 */
public class DeployContract {

    /**
     * log4j
     */
    private static final Logger logger = LoggerFactory.getLogger(DeployContract.class);

    /**
     * The Constant for default deploy contracts timeout.
     */
    private static final Integer DEFAULT_DEPLOY_CONTRACTS_TIMEOUT_IN_SECONDS = 15;

    /**
     * The context.
     */
    protected static ApplicationContext context;

    /**
     * The credentials.
     */
    protected static Credentials credentials;

    /**
     * web3j object
     */
    private static Web3j web3j;

    /**
     * contract address
     */
    private static String filePath;

    static {
        context = new ClassPathXmlApplicationContext("applicationContext.xml");
    }

    /**
     * The main method.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("Plese input your file path.");
            System.exit(0);
        }

        filePath = args[0];

        deployContract();
        System.exit(0);
    }

    /**
     * Load config.
     *
     * @return true, if successful
     */
    static boolean loadConfig() {

        Service service = context.getBean(Service.class);
        try {
            service.run();
        } catch (Exception e) {
            logger.error("[BaseService] Service init failed. ", e);
        }

        ChannelEthereumService channelEthereumService = new ChannelEthereumService();
        channelEthereumService.setChannelService(service);
        web3j = Web3j.build(channelEthereumService);
        if (null == web3j) {
            logger.error("[BaseService] web3j init failed. ");
            return false;
        }

        ToolConf toolConf = context.getBean(ToolConf.class);

        logger.info("begin init credentials");
        credentials = GenCredential.create(toolConf.getPrivKey());

        if (null == credentials) {
            logger.error("[BaseService] credentials init failed. ");
            return false;
        }

        return true;
    }

    /**
     * Gets the web3j.
     *
     * @return the web3j instance
     */
    protected static Web3j getWeb3j() {
        if (null == web3j) {
            loadConfig();
        }
        return web3j;
    }

    private static void deployContract() {
        String weIdContractAddress = deployWeIDContract();
        String authorityIssuerDataAddress = deployAuthorityIssuerContracts();
        deployCptContracts(authorityIssuerDataAddress, weIdContractAddress);
    }

    private static String deployWeIDContract() {
        if (null == web3j) {
            loadConfig();
        }
        Future<WeIdContract> f =
            WeIdContract.deploy(
                web3j,
                credentials,
                WeIdConstant.GAS_PRICE,
                WeIdConstant.GAS_LIMIT,
                WeIdConstant.INILITIAL_VALUE);

        try {
            WeIdContract weIDContract =
                f.get(DEFAULT_DEPLOY_CONTRACTS_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            String contractAddress = weIDContract.getContractAddress();
            writeAddressToFile("WeIDContract", contractAddress);
            return contractAddress;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
        }
        return StringUtils.EMPTY;
    }

    private static String deployCptContracts(
        String authorityIssuerDataAddress, String weIdContractAddress) {
        if (null == web3j) {
            loadConfig();
        }

        try {
            Future<CptData> f1 =
                CptData.deploy(
                    web3j,
                    credentials,
                    WeIdConstant.GAS_PRICE,
                    WeIdConstant.GAS_LIMIT,
                    WeIdConstant.INILITIAL_VALUE,
                    new Address(authorityIssuerDataAddress));
            CptData cptData = f1.get(DEFAULT_DEPLOY_CONTRACTS_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            String cptDataAddress = cptData.getContractAddress();
            //        writeAddressToFile("CptData", cptDataAddress);

            Future<CptController> f2 =
                CptController.deploy(
                    web3j,
                    credentials,
                    WeIdConstant.GAS_PRICE,
                    WeIdConstant.GAS_LIMIT,
                    WeIdConstant.INILITIAL_VALUE,
                    new Address(cptDataAddress),
                    new Address(weIdContractAddress));
            CptController cptController =
                f2.get(DEFAULT_DEPLOY_CONTRACTS_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            String cptControllerAddress = cptController.getContractAddress();
            writeAddressToFile("CptController", cptControllerAddress);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
        }

        return StringUtils.EMPTY;
    }

    private static String deployAuthorityIssuerContracts() {
        if (null == web3j) {
            loadConfig();
        }

        // Step 1: Deploy RoleController sol => [addr1]
        String authorityIssuerDataAddress = StringUtils.EMPTY;

        Future<RoleController> f1 =
            RoleController.deploy(
                web3j,
                credentials,
                WeIdConstant.GAS_PRICE,
                WeIdConstant.GAS_LIMIT,
                WeIdConstant.INILITIAL_VALUE);
        try {
            RoleController roleController =
                f1.get(DEFAULT_DEPLOY_CONTRACTS_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            String roleControllerAddress = roleController.getContractAddress();
            Future<CommitteeMemberData> f2 =
                CommitteeMemberData.deploy(
                    web3j,
                    credentials,
                    WeIdConstant.GAS_PRICE,
                    WeIdConstant.GAS_LIMIT,
                    WeIdConstant.INILITIAL_VALUE,
                    new Address(roleControllerAddress));
            try {
                CommitteeMemberData committeeMemberData =
                    f2.get(DEFAULT_DEPLOY_CONTRACTS_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
                String committeeMemberDataAddress = committeeMemberData.getContractAddress();
                Future<CommitteeMemberController> f3 =
                    CommitteeMemberController.deploy(
                        web3j,
                        credentials,
                        WeIdConstant.GAS_PRICE,
                        WeIdConstant.GAS_LIMIT,
                        WeIdConstant.INILITIAL_VALUE,
                        new Address(committeeMemberDataAddress),
                        new Address(roleControllerAddress));
                try {
                    Future<AuthorityIssuerData> f4 =
                        AuthorityIssuerData.deploy(
                            web3j,
                            credentials,
                            WeIdConstant.GAS_PRICE,
                            WeIdConstant.GAS_LIMIT,
                            WeIdConstant.INILITIAL_VALUE,
                            new Address(roleControllerAddress));
                    try {
                        AuthorityIssuerData authorityIssuerData =
                            f4.get(DEFAULT_DEPLOY_CONTRACTS_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
                        authorityIssuerDataAddress = authorityIssuerData.getContractAddress();
                        Future<AuthorityIssuerController> f5 =
                            AuthorityIssuerController.deploy(
                                web3j,
                                credentials,
                                WeIdConstant.GAS_PRICE,
                                WeIdConstant.GAS_LIMIT,
                                WeIdConstant.INILITIAL_VALUE,
                                new Address(authorityIssuerDataAddress),
                                new Address(roleControllerAddress));
                        try {
                            AuthorityIssuerController authorityIssuerController =
                                f5.get(DEFAULT_DEPLOY_CONTRACTS_TIMEOUT_IN_SECONDS,
                                    TimeUnit.SECONDS);
                            String authorityIssuerControllerAddress =
                                authorityIssuerController.getContractAddress();
                            writeAddressToFile("authorityIssuerController",
                                authorityIssuerControllerAddress);
                            return authorityIssuerControllerAddress;
                        } catch (Exception e) {
                            logger.error(
                                "AuthorityIssuerController deployment error:" + e.toString());
                        }
                    } catch (Exception e) {
                        logger.error("AuthorityIssuerData deployment error:" + e.toString());
                    }
                } catch (Exception e) {
                    logger.error("CommitteeMemberController deployment error:" + e.toString());
                }
            } catch (Exception e) {
                logger.error("CommitteeMemberData deployment error:" + e.toString());
            }
        } catch (Exception e) {
            logger.error("RoleController deployment error:" + e.toString());
        }
        return authorityIssuerDataAddress;
    }

    private static void writeAddressToFile(String contractName, String contractAddress) {

        FileWriter fileWritter = null;
        try {

            fileWritter = new FileWriter(filePath, true);
            String content =
                new StringBuffer()
                    .append(contractName)
                    .append("=")
                    .append(contractAddress)
                    .append("\r\n")
                    .toString();
            fileWritter.write(content);
            fileWritter.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != fileWritter) {
                try {
                    fileWritter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}