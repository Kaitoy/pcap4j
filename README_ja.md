[English](https://github.com/kaitoy/pcap4j)

<img alt="Pcap4J" title="Pcap4J" src="https://github.com/kaitoy/pcap4j/raw/master/www/images/logos/pcap4j-logo-color.png" width="70%" style="margin: 0px auto; display: block;" />

[ロゴ](https://github.com/kaitoy/pcap4j/blob/master/www/logos.md)

[![Slack](http://pcap4j-slackin.herokuapp.com/badge.svg)](https://pcap4j-slackin.herokuapp.com/)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.pcap4j/pcap4j-distribution/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.pcap4j/pcap4j-distribution)

[![Build Status](https://travis-ci.org/kaitoy/pcap4j.svg?branch=master)](https://travis-ci.org/kaitoy/pcap4j)
[![CircleCI](https://circleci.com/gh/kaitoy/pcap4j/tree/master.svg?style=svg)](https://circleci.com/gh/kaitoy/pcap4j/tree/master)
[![Build status](https://ci.appveyor.com/api/projects/status/github/kaitoy/pcap4j?branch=master&svg=true)](https://ci.appveyor.com/project/kaitoy/pcap4j/branch/master)
[![codecov](https://codecov.io/gh/kaitoy/pcap4j/branch/master/graph/badge.svg)](https://codecov.io/gh/kaitoy/pcap4j)

Pcap4J 2.x and newer
====================

パケットをキャプチャ・作成・送信するためのJavaライブラリ。
ネイティブのパケットキャプチャライブラリである[libpcap](http://www.tcpdump.org/)、
[Npcap](https://github.com/nmap/npcap)、または[WinPcap](http://www.winpcap.org/)を[JNA](https://github.com/twall/jna)を
使ってラッピングして、JavaらしいAPIに仕上げたもの。

目次
----

* [ダウンロード](#ダウンロード)
* [機能](#機能)
* [使い方](#使い方)
    * [システム要件](#システム要件)
        * [ライブラリ等の依存](#ライブラリ等の依存)
        * [プラットフォーム](#プラットフォーム)
        * [その他](#その他)
    * [ドキュメント](#ドキュメント)
    * [サンプル実行方法](#サンプル実行方法)
    * [プロジェクトへPcap4Jを追加する方法](#プロジェクトへpcap4jを追加する方法)
    * [ネイティブライブラリのロードについて](#ネイティブライブラリのロードについて)
        * [WinPcapかNpcapか](#WinPcapかNpcapか)
    * [Docker](#docker)
* [ビルド](#ビルド)
* [ライセンス](#ライセンス)
* [コンタクト](#コンタクト)

ダウンロード
------------

Maven Central Repositoryからダウンロードできる。

* Pcap4J 2.0.0-alpha
    * [pcap4j-2.0.0-alpha-distribution.zip](http://search.maven.org/remotecontent?filepath=org/pcap4j/pcap4j/2.0.0-alpha/pcap4j-2.0.0-alpha-distribution.zip)
* スナップショットビルド
    * https://oss.sonatype.org/content/repositories/snapshots/org/pcap4j/pcap4j/

機能
----

* ネットワークインターフェースからパケットをキャプチャし、Javaのオブジェクトに変換する。
* パケットオブジェクトにアクセスしてパケットのフィールドを取得できる。
* 手動でパケットオブジェクトを組み立てることもできる。
* パケットオブジェクトを現実のパケットに変換してネットワークに送信できる。
* 以下のプロトコルに対応。
    * Ethernet、Linux SLL、raw IP、PPP (RFC1661、RFC1662)、BSD (Mac OS X) loopback encapsulation、Radiotap
    * IEEE 802.11
        * Probe Request
    * LLC、SNAP
    * IEEE802.1Q
    * ARP
    * IPv4 (RFC791、RFC1349)、IPv6 (RFC2460)
    * ICMPv4 (RFC792)、ICMPv6 (RFC4443, RFC4861)
    * TCP (RFC793、RFC2018、draft-ietf-tcpm-1323bis-21)、UDP、SCTP (共通ヘッダのみ)
    * GTPv1 (GTP-UとGTP-Cのヘッダのみ)
    * DNS (RFC1035、RFC3596、RFC6844)
* 各ビルトインパケットクラスはシリアライズに対応。スレッドセーフ(実質的に不変)。
* ライブラリをいじらずに、対応プロトコルをユーザが追加できる。
* pcapのダンプファイル(Wiresharkのcapture fileなど)の読み込み、書き込み。
* [Semantic Versioning 2.0.0](https://semver.org/lang/ja/)準拠。

使い方
------

#### システム要件 ####

##### ライブラリ等の依存 #####
JRE 8以降で動く。
UNIX系ならlibpcap 1.0.0以降、WindowsならNpcapかWinPcap (多分)3.0以降がインストールされている必要がある。
jna 4以降、slf4j-api(と適当なロガー実装モジュール)もクラスパスに含める必要がある。

動作確認に使っているバージョンは以下。

* libpcap 1.1.1
* WinPcap 4.1.2
* jna 4.1.0
* slf4j-api 1.7.12
* logback-core 1.0.0
* logback-classic 1.0.0

##### プラットフォーム #####
x86かx64プロセッサ上の以下のOSで動作することを確認した。

* Windows: XP, Vista, 7, [10](http://tbd.kaitoy.xyz/2016/01/12/pcap4j-with-four-native-libraries-on-windows10/), 2003 R2, 2008, 2008 R2, and 2012
* OS X
* Linux
    * RHEL: 5 and 6
    * CentOS: 5
    * Ubuntu: 13
* UNIX
    * Solaris: 10
    * FreeBSD: 10

他のアーキテクチャ/OSでも、JNAとlibpcapがサポートしていれば動く、と願う。

##### その他 #####
Pcap4Jは管理者権限で実行する必要がある。
ただし、Linuxの場合、javaコマンドにケーパビリティ`CAP_NET_RAW`と`CAP_NET_ADMIN`を与えれば、非rootユーザでも実行できる。
ケーパビリティを付与するには次のコマンドを実行する: `setcap cap_net_raw,cap_net_admin=eip /path/to/java`

#### ドキュメント ####
最新のJavaDocは[こちら](http://www.javadoc.io/doc/org.pcap4j/pcap4j/2.0.0-alpha)。
各バージョンのJavaDocは[Maven Central Repository](http://search.maven.org/#search|ga|1|g%3A%22org.pcap4j%22)からダウンロードできる。

Pcap4Jのモジュール構成については[こちら](https://github.com/kaitoy/pcap4j/blob/master/www/pcap4j_modules.md)。

Pcap4Jはpcapネイティブライブラリのラッパーなので、以下のドキュメントを読むとPcap4Jの使い方がわかる。

* [Programming with pcap](http://www.tcpdump.org/pcap.html)
* [WinPcap Manuals](http://www.winpcap.org/docs/default.htm)
* [pcap API と Pcap4J API の対応](https://github.com/kaitoy/pcap4j/blob/master/www/api_mappings.md)

Pcap4Jプログラムの書き方は[サンプル](https://github.com/kaitoy/pcap4j/tree/master/pcap4j-sample/src/main/java/org/pcap4j/sample)を見ると理解しやすい。

さらにPcap4Jを理解するには以下のドキュメントを参照。

* [Learn about packet class](https://github.com/kaitoy/pcap4j/blob/master/www/Packet.md)
* [Learn about Packet Factory](https://github.com/kaitoy/pcap4j/blob/master/www/PacketFactory.md)
* [サポートプロトコル追加方法](https://github.com/kaitoy/pcap4j/blob/master/www/HowToAddProtocolSupport.md)
* [kaitoy's blog](http://tbd.kaitoy.xyz/tags/pcap4j/)

#### サンプル実行方法 ####
以下の例を参照。

* [org.pcap4j.sample.Loop](https://github.com/kaitoy/pcap4j/blob/master/www/sample_Loop_ja.md)
* [org.pcap4j.sample.SendArpRequest](https://github.com/kaitoy/pcap4j/blob/master/www/sample_SendArpRequest_ja.md)

Eclipse上でpcap4j-sampleにあるサンプルを実行する場合、
その実行構成のクラスパスタブのユーザー・エントリーの最初に、
pcap4j-packetfactory-staticプロジェクトかpcap4j-packetfactory-propertiesbasedプロジェクトを追加する必要がある。

#### プロジェクトへPcap4Jを追加する方法 ####

* Gradle

    build.gradleに以下のような記述を追加する。

    ```
    dependencies {
      compile 'org.pcap4j:pcap4j-core:2.0.0-alpha'
      compile 'org.pcap4j:pcap4j-packetfactory-static:2.0.0-alpha'
    }
    ```

* Maven

    pom.xmlに以下のような記述を追加する。

    ```xml
    <project xmlns="http://maven.apache.org/POM/4.0.0"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                          http://maven.apache.org/xsd/maven-4.0.0.xsd">
      ...
      <dependencies>
        <dependency>
          <groupId>org.pcap4j</groupId>
          <artifactId>pcap4j-core</artifactId>
          <version>2.0.0-alpha</version>
        </dependency>
        <dependency>
          <groupId>org.pcap4j</groupId>
          <artifactId>pcap4j-packetfactory-static</artifactId>
          <version>2.0.0-alpha</version>
        </dependency>
           ...
      </dependencies>
      ...
    </project>
    ```

#### ネイティブライブラリのロードについて ####
デフォルトでは下記の条件でネイティブライブラリを検索し、ロードする。

* Windows
    * サーチパス: 環境変数`PATH`に含まれるパス等([MSDN](https://msdn.microsoft.com/ja-jp/library/7d83bc18.aspx)参照。)と`%SystemRoot%\System32\Npcap`。
    * ファイル名: wpcap.dllとPacket.dll
* Linux/UNIX
    * サーチパス: OSに設定された共有ライブラリのサーチパス。例えば環境変数`LD_LIBRARY_PATH`に含まれるパス。
    * ファイル名: libpcap.so
* Mac OS X
    * サーチパス: OSに設定された共有ライブラリのサーチパス。例えば環境変数`DYLD_LIBRARY_PATH`に含まれるパス。
    * ファイル名: libpcap.dylib

カスタマイズのために、以下のJavaのシステムプロパティが使える。

* jna.library.path: サーチパスを指定する。
* org.pcap4j.core.pcapLibName: pcapライブラリ(wpcap.dllかlibpcap.soかlibpcap.dylib)へのフルパスを指定する。
* (Windowsのみ) org.pcap4j.core.packetLibName: packetライブラリ(Packet.dll)へのフルパスを指定する。

##### WinPcapかNpcapか #####
Windowsのネイティブpcapライブラリの選択肢にはWinPcapとNpcapがある。

WinPcapは2013/3/8に4.1.3(libpcap 1.0.0ベース)をリリースして以来開発が止まっているのに対して、
Npcapは現在も開発が続いているので、より新しい機能を使いたい場合などにはNpcapを選ぶといい。

デフォルトでは、WinPcapは`%SystemRoot%\System32\`にインストールされ、Npcapは`%SystemRoot%\System32\Npcap\`にインストールされる。
両方インストールしている環境で、明示的にNpcapを使用したい場合、`org.pcap4j.core.pcapLibName`に`%SystemRoot%\System32\Npcap\wpcap.dll`を指定して、`org.pcap4j.core.packetLibName`に`%SystemRoot%\System32\Npcap\Packet.dll`を指定する。

### Docker ###

[![](https://images.microbadger.com/badges/image/kaitoy/pcap4j.svg)](https://microbadger.com/images/kaitoy/pcap4j)

CentOSのPcap4J実行環境を構築したDockerイメージが[Docker Hub](https://registry.hub.docker.com/u/kaitoy/pcap4j/)にある。

`docker pull kaitoy/pcap4j`でダウンロードし、`docker run kaitoy/pcap4j:latest`でコンテナのeth0のパケットキャプチャーを実行できる。

このイメージはGitレポジトリにコミットがあるたびにビルドされる。

ビルド
------

1. WinPcap/Npcap/libpcapインストール:<br>
   WindowsであればNpcapかWinPcap、Linux/Unixであればlibpcapをインストールする。
   ビルド時に実行されるunit testで必要なので。
2. JDK 8+インストール:<br>
   JDKの8以上をダウンロードしてインストール。JAVA_HOMEを設定する。
3. Gitをインストール:<br>
   [Git](http://git-scm.com/downloads)をダウンロードしてインストールする。
   Gitのインストールはビルドに必須ではないので、このステップはスキップしてもよい。
4. Pcap4Jのレポジトリのダウンロード:<br>
   Gitをインストールした場合は`git clone git@github.com:kaitoy/pcap4j.git` を実行する。
   インストールしていない場合は、[zip](https://github.com/kaitoy/pcap4j/zipball/master)でダウンロードして展開する。
5. ビルド:<br>
   プロジェクトのルートディレクトリに`cd`して、`gradlew build` を実行する。
   unit testを通すためにはAdministrator/root権限が必要。

ライセンス
----------

Pcap4J is distributed under the MIT license.

    Copyright (c) 2011-2017 Pcap4J.org

    Permission is hereby granted, free of charge, to any person obtaining a copy of
    this software and associated documentation files (the "Software"), to deal in the Software without restriction,
    including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
    subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
    NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
    IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
    WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

    以下に定める条件に従い、本ソフトウェアおよび関連文書のファイル（以下「ソフトウェア」）の複製を取得するすべての人に対し、
    ソフトウェアを無制限に扱うことを無償で許可します。これには、ソフトウェアの複製を使用、複写、変更、結合、掲載、頒布、サブライセンス、
    および/または販売する権利、およびソフトウェアを提供する相手に同じことを許可する権利も無制限に含まれます。
    上記の著作権表示および本許諾表示を、ソフトウェアのすべての複製または重要な部分に記載するものとします。

    ソフトウェアは「現状のまま」で、明示であるか暗黙であるかを問わず、何らの保証もなく提供されます。
    ここでいう保証とは、商品性、特定の目的への適合性、および権利非侵害についての保証も含みますが、それに限定されるものではありません。
    作者または著作権者は、契約行為、不法行為、またはそれ以外であろうと、ソフトウェアに起因または関連し、
    あるいはソフトウェアの使用またはその他の扱いによって生じる一切の請求、損害、その他の義務について何らの責任も負わないものとします。

コンタクト
----------

Kaito Yamada (kaitoy@pcap4j.org)
