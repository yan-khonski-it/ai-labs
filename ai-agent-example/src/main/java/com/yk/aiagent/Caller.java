package com.yk.aiagent;

import java.io.IOException;

public interface Caller {

  String call(String content) throws IOException, InterruptedException;

}
