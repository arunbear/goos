package com.belano.auctionsniper;

import com.belano.testcontainers.OpenfireContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.google.common.net.HostAndPort;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@Testcontainers
@TestInstance(PER_CLASS)
@Tag("integration")
public class AuctionSniperEndToEndTest {

    private static final Logger logger = LoggerFactory.getLogger(AuctionSniperEndToEndTest.class);

    @Container
    private static final OpenfireContainer container = new OpenfireContainer();
    private static HostAndPort hostAndPort;

    private ApplicationRunner application;
    private FakeAuctionServer auction;
    private FakeAuctionServer anotherAuction;

    @BeforeAll
    public void setUpOpenfire() {
        hostAndPort = HostAndPort.fromParts(container.getHost(), container.getMappedPort(5222));
        logger.info(">> Container id: {} - Openfire running at {}", container.getContainerId(), hostAndPort);
        container.setup();
    }

    @BeforeEach
    public void setUp() {
        auction = new FakeAuctionServer(hostAndPort, "item-54321");
        anotherAuction = new FakeAuctionServer(hostAndPort, "item-65432");
        application = new ApplicationRunner(hostAndPort);
    }

    @Test
    void sniperJoinsAuctionUntilAuctionCloses() throws Exception {
        auction.startSellingItem();
        application.startBiddingIn(auction);
        auction.hasReceivedJoinRequestFrom(ApplicationRunner.SNIPER_XMPP_ID_REGEX);
        auction.announceClosed();
        application.showsSniperHasLostAuction();
    }

    @Test
    void sniperMakesAHigherBidButLoses() throws Exception {
        auction.startSellingItem();
        application.startBiddingIn(auction);
        auction.hasReceivedJoinRequestFrom(ApplicationRunner.SNIPER_XMPP_ID_REGEX);
        auction.reportPrice(1000, 98, "other bidder");
        application.hasShownSniperIsBidding(auction, 1000, 1098);
        auction.hasReceivedBid(1098, ApplicationRunner.SNIPER_XMPP_ID_REGEX);
        auction.announceClosed();
        application.showsSniperHasLostAuction();
    }

    @Test
    void sniperWinsAnAuctionByBiddingHigher() throws Exception {
        auction.startSellingItem();
        application.startBiddingIn(auction);
        auction.hasReceivedJoinRequestFrom(ApplicationRunner.SNIPER_XMPP_ID_REGEX);
        auction.reportPrice(1000, 98, "other bidder");
        application.hasShownSniperIsBidding(auction, 1000, 1098); // last price, last bid
        auction.hasReceivedBid(1098, ApplicationRunner.SNIPER_XMPP_ID_REGEX);
        auction.reportPrice(1098, 97, ApplicationRunner.SNIPER_ID);
        application.hasShownSniperIsWinning(auction, 1098); // winning bid
        auction.announceClosed();
        application.showsSniperHasWonAuction(auction, 1098); // last price
    }

    @Test
    void sniperBidsForMultipleItems() throws Exception {
        auction.startSellingItem();
        anotherAuction.startSellingItem();

        application.startBiddingIn(auction, anotherAuction);
        auction.hasReceivedJoinRequestFrom(ApplicationRunner.SNIPER_XMPP_ID_REGEX);
        anotherAuction.hasReceivedJoinRequestFrom(ApplicationRunner.SNIPER_XMPP_ID_REGEX);

        auction.reportPrice(1000, 98, "other bidder");
        auction.hasReceivedBid(1098, ApplicationRunner.SNIPER_XMPP_ID_REGEX);

        anotherAuction.reportPrice(500, 21, "other bidder");
        anotherAuction.hasReceivedBid(521, ApplicationRunner.SNIPER_XMPP_ID_REGEX);

        auction.reportPrice(1098, 97, ApplicationRunner.SNIPER_ID);
        anotherAuction.reportPrice(521, 22, ApplicationRunner.SNIPER_ID);

        application.hasShownSniperIsWinning(auction, 1098);
        application.hasShownSniperIsWinning(anotherAuction, 521);

        auction.announceClosed();
        anotherAuction.announceClosed();

        application.showsSniperHasWonAuction(auction, 1098);
        application.showsSniperHasWonAuction(anotherAuction, 521);
    }

    @AfterEach
    void tearDown() {
        auction.stop();
        application.stop();
    }
}
