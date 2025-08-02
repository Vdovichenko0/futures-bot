package io.cryptobot.configs.service;

import io.cryptobot.websocket.BinanceWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartApp implements ApplicationRunner {
//    private final OrderServiceImpl orderService;
//    private final BybitServiceImpl bybitService;
//    private final MainHelper mainHelper;
    private final BinanceWebSocketService binanceWebSocketService;
//    private final BinanceSpotUserDataStreamServiceNew webSocketServiceUserData;
//    private final KlineService klineService;
//    private final DepthService depthService;

    @Override
    public void run(ApplicationArguments args) {
//        log.info("✅ call -> orderService.init();");
//        orderService.init();
//        log.info("✅ call -> mainHelper.getAllSymbols_AUTO()");
//        mainHelper.getAllSymbols_AUTO();
        log.info("✅ call -> binanceWebSocketService.start()");
        binanceWebSocketService.start();
//        log.info("✅ call -> webSocketServiceUserData.init()");
//        webSocketServiceUserData.init();
//        log.info("✅ call -> klineService.fetchInitialKlines()");
//        klineService.fetchInitialKlines();
//        log.info("✅ call -> depthService.initializeOrderBooks()");
//        depthService.initializeOrderBooks();
//        log.info("✅ call -> walletService.autoUpdate()");
    }
}