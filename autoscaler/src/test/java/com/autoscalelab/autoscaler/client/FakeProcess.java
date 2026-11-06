package com.autoscalelab.autoscaler.client;

/**
 * Petit programme utilisé par CommandRunnerTest pour simuler une commande externe.
 *
 * Usage : print &lt;texte&gt; | fail &lt;code&gt; &lt;message&gt; | sleep &lt;millisecondes&gt;
 */
public final class FakeProcess {

    private FakeProcess() {
    }

    public static void main(String[] args) throws InterruptedException {
        switch (args[0]) {
            case "print" -> System.out.println(args[1]);
            case "fail" -> {
                System.err.println(args[2]);
                System.exit(Integer.parseInt(args[1]));
            }
            case "sleep" -> Thread.sleep(Long.parseLong(args[1]));
            default -> System.exit(2);
        }
    }
}
