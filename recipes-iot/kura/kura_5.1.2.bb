SUMMARY = "Kura"
DESCRIPTION = "Kura"
LICENSE = "EPL-1.0"
LIC_FILES_CHKSUM = " \
    file://LICENSE;md5=0a41ba798cc1e1772a98a4888f1d8709 \
"
FILESEXTRAPATHS_prepend := "${THISDIR}/files:"

SRC_URI = " \
    git://git@github.com/SolidRun/SolidSense-V1.git;protocol=ssh;protocol=https;branch=master;destsuffix=SolidSense-V1;name=SolidSense-V1 \
    git://github.com/solidsense-connect/kura.git;protocol=https;branch=solidsense-5.1.2;destsuffix=kura-${PV};name=kura \
    file://polkit.kura \
    file://bluetooth.conf \
"
SRCREV_SolidSense-V1 = "d7557fb7fb3f1e12aabcf905455f834b425b46bc"
SRCREV_kura = "9d33114d51335ab747c81f72c854984851c4d102"
S-V1 = "${WORKDIR}/SolidSense-V1"
S-KURA = "${WORKDIR}/kura-${PV}"
KURA_VERSION = "${PV}"
KURA_PROFILE = "solidsense-igw"
KURA_VERSION_PATH = "/opt/eclipse/kura_${KURA_VERSION}_${KURA_PROFILE}"

SYSTEMD_SERVICE_${PN} = "kura.service firewall.service"
SYSTEMD_AUTO_ENABLE_${PN} = "enable"

DEPENDS = " \
    maven-native \
    openssl \
    openjdk-11-native \
"
RDEPENDS_${PN} = " \
    bash \
    ca-certificates-java \
    openjdk-11 \
    openssl \
    python3 \
"

JAVA_HOME="${WORKDIR}/recipe-sysroot-native/usr/lib/jvm/openjdk-11-native"

inherit systemd useradd

USERADD_PACKAGES = "${PN}"
# USERADD_PARAM_${PN} = " \
#     --no-create-home --system --password '' --shell /sbin/nologin --user-group kura; \
#     --system --password '' --groups dialout --user-group kurad; \
# "
USERADD_PARAM_${PN} = " \
    -M --password '' --shell /sbin/nologin kura; \
    -r -M --password '' --groups dialout kurad; \
"
USERMOD_PARAM_${PN} = " \
    --lock kura \
    --lock kurad \
"

do_compile () {
    export JAVA_HOME="${JAVA_HOME}"

    # Kura
    cd ${S-KURA}
    #Build only solidsense-igw
    ./build-all.sh -P!can-dp -P!core-dp -P!dev-env -P!intel-up2-ubuntu-20 -P!intel-up2-ubuntu-20-nn -Psolidsense-igw -P!nvidia-jetson-nano -P!nvidia-jetson-nano-nn -P!raspberry-pi -P!raspberry-pi-nn -P!raspberry-pi-ubuntu-20 -P!raspberry-pi-ubuntu-20-nn

    # Custom plugins
    #cd ${SRC_SS}/Kura/LTE/org.eclipse.kura.linux.net
    #mvn -f pom.xml clean install ${MAVEN_PROPS}
    #cd ${SRC_SS}/Kura/LTE/org.eclipse.kura.net.admin
    #mvn -f pom.xml clean install ${MAVEN_PROPS}
}

do_install () {
    # Install Kura from zip file
    install -d ${D}/opt/eclipse
    cd ${D}/opt/eclipse
    unzip ${S-KURA}/kura/distrib/target/kura_${KURA_VERSION}_${KURA_PROFILE}.zip

    # Mimic Kura kura_install.sh script

    INSTALL_DIR=/opt/eclipse

    #create known kura install location
    ln -sf kura_${KURA_VERSION}_${KURA_PROFILE}* ${D}${INSTALL_DIR}/kura

    #set up Kura init
    install -d ${D}${systemd_unitdir}/system
    sed "s|INSTALL_DIR|${INSTALL_DIR}|" ${D}${INSTALL_DIR}/kura/install/kura.service
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/kura.service ${D}${systemd_unitdir}/system/kura.service
    
    # setup snapshot_0 recovery folder
    install -d ${D}${INSTALL_DIR}/kura/.data

    install -d ${D}${INSTALL_DIR}/kura/data

    # setup /etc/sysconfig folder for iptables configuration file
    install -d ${D}${sysconfdir}/sysconfig

    #set up users and grant permissions to them    
        # --- manage_kura_users.sh ---
    # add polkit policy
    install -d ${D}${datadir}/polkit-1/rules.d
    install -m 0600 ${WORKDIR}/polkit.kura ${D}${datadir}/polkit-1/rules.d/kura.rules

    # grant kurad user the privileges to manage ble via dbus
    install -m 0644 ${WORKDIR}/bluetooth.conf ${D}${INSTALL_DIR}/kura/.data/bluetooth.conf
    # cp ${D}${sysconfdir}/dbus-1/system.d/bluetooth.conf ${D}${sysconfdir}/dbus-1/system.d/bluetooth.conf.save

    # Post install :
    # install -d ${D}${sysconfdir}/dbus-1/system.d/
    # install -m 0644 ${WORKDIR}/bluetooth.conf ${D}${sysconfdir}/dbus-1/system.d/bluetooth.conf
        # --- END manage_kura_users.sh ---
    
# --------- ?
    # # setup kurad user
    # install -d ${D}${sysconfdir}/sudoers.d
    # install -m 0600 ${WORKDIR}/sudoers.kurad ${D}${sysconfdir}/sudoers.d/kurad
# --------- ?

    #set up default networking file
    install -d ${D}${sysconfdir}/network
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/network.interfaces ${D}${sysconfdir}/network/interfaces
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/network.interfaces ${D}${INSTALL_DIR}/kura/.data/interfaces

    #set up network helper scripts
    install -d ${D}${sysconfdir}/network/if-up.d
    install -d ${D}${sysconfdir}/network/if-down.d
    install -m 0744 ${D}${INSTALL_DIR}/kura/install/ifup-local.debian ${D}${sysconfdir}/network/if-up.d/ifup-local
    install -m 0744 ${D}${INSTALL_DIR}/kura/install/ifdown-local ${D}${sysconfdir}/network/if-down.d/idown-local

    #set up default firewall configuration
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/iptables.init ${D}${INSTALL_DIR}/kura/.data/iptables
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/iptables.init ${D}${sysconfdir}/sysconfig/iptables
    install -m 0755 ${D}${INSTALL_DIR}/kura/install/firewall.init ${D}${INSTALL_DIR}/kura/bin/firewall
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/firewall.service ${D}${systemd_unitdir}/system/firewall.service
    sed -i "s|/bin/sh KURA_DIR|/bin/bash ${INSTALL_DIR}/kura|" ${D}${systemd_unitdir}/system/firewall.service

    #copy snapshot_0.xml
    install -m 0644 ${D}${INSTALL_DIR}/kura/user/snapshots/snapshot_0.xml ${D}${INSTALL_DIR}/kura/.data/snapshot_0.xml

    #disable NTP service ?

    # Prevent time sync services from starting
    rm -rf ${D}${sysconfdir}/systemd/system/sysinit.target.wants/systemd-timesyncd.service

    # Prevent time sync with chrony from starting.
    rm -rf ${D}${sysconfdir}/systemd/system/sysinit.target.wants/chrony.service

    #set up networking configuration
    #Update SSID ?

    # dhcpd config for eth0
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/dhcpd-eth0.conf ${D}${sysconfdir}/dhcpd-eth0.conf
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/dhcpd-eth0.conf ${D}${INSTALL_DIR}/kura/.data/dhcpd-eth0.conf
    # dhcpd config for wlan0
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/dhcpd-wlan0.conf ${D}${sysconfdir}/dhcpd-wlan0.conf
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/dhcpd-wlan0.conf ${D}${INSTALL_DIR}/kura/.data/dhcpd-wlan0.conf

    #set up bind/named
    install -d ${D}${sysconfdir}/bind
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/named.conf ${D}${sysconfdir}/bind/named.conf
    install -d ${D}${localstatedir}/named
    # chown -R bind ${D}${localstatedir}/named
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/named.ca ${D}${localstatedir}/named/named.ca
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/named.rfc1912.zones ${D}${sysconfdir}/named.rfc1912.zones
    install -d ${D}${sysconfdir}/apparmor.d
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/usr.sbin.named ${D}${sysconfdir}/apparmor.d/usr.sbin.named
    # if [ ! -f "${D}${sysconfdir}/bind/rndc.key" ] ; then
	#     rndc-confgen -r /dev/urandom -a
    # fi
    # chown bind:bind ${D}${sysconfdir}/bind/rndc.key
    # chmod 600 ${D}${sysconfdir}/bind/rndc.key

    #set up logrotate - no need to restart as it is a cronjob
    install ${D}${INSTALL_DIR}/kura/install/kura.logrotate ${D}${sysconfdir}/logrotate-kura.conf

    if [ ! -f ${D}${sysconfdir}/cron.d/logrotate-kura ]; then
        test -d ${D}${sysconfdir}/cron.d || mkdir -p ${D}${sysconfdir}/cron.d
        touch ${D}${sysconfdir}/cron.d/logrotate-kura
        echo "*/5 * * * * root /usr/sbin/logrotate --state /var/log/logrotate-kura.status /etc/logrotate-kura.conf" >> ${D}${sysconfdir}/cron.d/logrotate-kura
    fi

    #set up systemd-tmpfiles
    install -d ${D}${sysconfdir}/tmpfiles.d
    install -m 0644 ${D}${INSTALL_DIR}/kura/install/kura-tmpfiles.conf ${D}${sysconfdir}/tmpfiles.d/kura.conf

    # disable dhcpcd service - kura is the network manager
    rm -rf ${D}${sysconfdir}/systemd/system/sysinit.target.wants/dhcpcd.service
    # disable isc-dhcp-server service - kura is the network manager
    rm -rf ${D}${sysconfdir}/systemd/system/sysinit.target.wants/isc-dhcp-server.service
    #disable wpa_supplicant
    rm -rf ${D}${sysconfdir}/systemd/system/sysinit.target.wants/wpa_supplicant.service

    #assigning possible .conf files ownership to kurad
    PATTERN="${D}${sysconfdir}/dhcpd*.conf* ${D}${sysconfdir}/resolv.conf* ${D}${sysconfdir}/wpa_supplicant*.conf* ${D}${sysconfdir}/hostapd*.conf*"
    for FILE in $(ls $PATTERN 2>/dev/null)
    do
    chown kurad:kurad $FILE
    done

    # set up kura files permissions
    chmod 700 ${D}${INSTALL_DIR}/kura/bin/*.sh
    chown -R kurad:kurad ${D}/opt/eclipse
    chmod -R go-rwx ${D}/opt/eclipse
    chmod a+rx ${D}/opt/eclipse
    find ${D}/opt/eclipse/kura -type d -exec chmod u+x "{}" \;

    # keytool -genkey -alias localhost -keyalg RSA -keysize 2048 -keystore ${D}/opt/eclipse/kura/user/security/httpskeystore.ks -deststoretype pkcs12 -dname "CN=Kura, OU=Kura, O=Eclipse Foundation, L=Ottawa, S=Ontario, C=CA" -ext ku=digitalSignature,nonRepudiation,keyEncipherment,dataEncipherment,keyAgreement,keyCertSign -ext eku=serverAuth,clientAuth,codeSigning,timeStamping -validity 1000 -storepass changeit -keypass changeit


    # # Install updated start_kura_background.sh
    # install -d ${D}${INSTALL_DIR}/kura/bin
    # install -m 0755 ${S-V1}/Kura/scripts/start_kura_background.sh ${D}${INSTALL_DIR}/kura/bin/start_kura_background.sh

    # Install shell script to assist with running cli command via Kura/Kapua
    # install -d ${D}${base_bindir}
    # install -m 0755 ${S-V1}/Kura/scripts/krc.sh ${D}${base_bindir}/krc

    # # Install updated logging config
    # install -d ${D}${INSTALL_DIR}/kura/user
    # install -m 0644 ${S-V1}/Kura/user/log4j.xml ${D}${INSTALL_DIR}/kura/user/log4j.xml

    # Install the log configuration service
    # install -d ${D}${INSTALL_DIR}/kura/packages
    # install -m 0655 ${S-V1}/Kura/logs/com.solidsense.kura.LogConfigurationService/resources/dp/LogConfigurationService.dp \
    #     ${D}${INSTALL_DIR}/kura/packages/LogConfigurationService_1.0.0.dp

    # Delete unneeded files
    # rm ${D}/opt/eclipse/kura_${KURA_VERSION}_solid_sense/user/snapshots/snapshot_0.xml

    # Install snapshot_0.xml to template directory
    # install -d ${D}/opt/SolidSense/template/kura/
    # install -m 0644 ${D}${INSTALL_DIR}/kura/user/snapshots/snapshot_0.xml ${D}/opt/SolidSense/template/kura/snapshot_0.xml
}

pkg_postinst_${PN} () {
    # grant kurad user the privileges to manage ble via dbus
    cp $D/etc/dbus-1/system.d/bluetooth.conf $D/etc/dbus-1/system.d/bluetooth.conf.save
    cp $D/opt/eclipse/kura/.data/bluetooth.conf $D/etc/dbus-1/system.d/bluetooth.conf
}

FILES_${PN} = " \
    /opt \
    /usr \
    /opt/eclipse \
    /opt/eclipse/kura \
    /opt/eclipse/kura_5.1.2_solidsense-igw \
    /opt/eclipse/kura_5.1.2_solidsense-igw/notice.html \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install \
    /opt/eclipse/kura_5.1.2_solidsense-igw/bin \
    /opt/eclipse/kura_5.1.2_solidsense-igw/.data \
    /opt/eclipse/kura_5.1.2_solidsense-igw/framework \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins \
    /opt/eclipse/kura_5.1.2_solidsense-igw/data \
    /opt/eclipse/kura_5.1.2_solidsense-igw/log4j \
    /opt/eclipse/kura_5.1.2_solidsense-igw/user \
    /opt/eclipse/kura_5.1.2_solidsense-igw/console \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/manage_kura_users.sh \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/monit.init.raspbian \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/firewall.service \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/sysctl.kura.conf \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/named.conf \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/named.rfc1912.zones \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/ifup-local.debian \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/kura_install.sh \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/kura.init.raspbian \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/kura.service \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/dhcpd-eth0.conf \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/hostapd.conf \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/patch_sysctl.sh \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/firewall.init \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/kura.init.yocto \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/dhcpd-wlan0.conf \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/ifup-local \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/ifdown-local \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/monitrc.raspbian \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/named.ca \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/network.interfaces \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/kura.logrotate \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/iptables.init \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/kura-tmpfiles.conf \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/usr.sbin.named \
    /opt/eclipse/kura_5.1.2_solidsense-igw/install/ifup-local.raspbian \
    /opt/eclipse/kura_5.1.2_solidsense-igw/bin/start_kura_debug.sh \
    /opt/eclipse/kura_5.1.2_solidsense-igw/bin/start_kura_background.sh \
    /opt/eclipse/kura_5.1.2_solidsense-igw/bin/firewall \
    /opt/eclipse/kura_5.1.2_solidsense-igw/bin/start_kura.sh \
    /opt/eclipse/kura_5.1.2_solidsense-igw/.data/dhcpd-eth0.conf \
    /opt/eclipse/kura_5.1.2_solidsense-igw/.data/dhcpd-wlan0.conf \
    /opt/eclipse/kura_5.1.2_solidsense-igw/.data/interfaces \
    /opt/eclipse/kura_5.1.2_solidsense-igw/.data/iptables \
    /opt/eclipse/kura_5.1.2_solidsense-igw/.data/snapshot_0.xml \
    /opt/eclipse/kura_5.1.2_solidsense-igw/framework/RELEASE_NOTES.txt \
    /opt/eclipse/kura_5.1.2_solidsense-igw/framework/kura.properties \
    /opt/eclipse/kura_5.1.2_solidsense-igw/framework/jdk.dio.properties \
    /opt/eclipse/kura_5.1.2_solidsense-igw/framework/jdk.dio.policy \
    /opt/eclipse/kura_5.1.2_solidsense-igw/framework/config.ini \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/io.netty.transport-native-epoll_4.1.68.Final.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.sun.xml.bind.jaxb-osgi_2.3.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.jsp.jasper_1.1.500.v20200422-1833.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/usb4java-javax_1.3.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.http.server.manager_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.container.provider_1.0.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.useradmin_1.2.0.v20200807-1148.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.eclipsesource.jaxrs.provider.gson_2.3.0.201602281253.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.device_1.1.0.v20200810-0747.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.commons.lang3_3.4.0.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/io.netty.buffer_4.1.68.Final.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.core.variables_3.4.800.v20200120-1101.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.rest.asset.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.cloudconnection.eclipseiot.mqtt.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.log.stream_1.0.300.v20200828-1034.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.log4j2-api-config_1.0.0.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.net.admin_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/jdk.dio.aarch64_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.cm_1.4.400.v20200422-1833.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.camel.camel-amqp_2.25.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/minimal-json_0.9.5.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.configuration_2.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.frameworkadmin.equinox_1.1.400.v20200319-1546.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/jakarta.xml.ws-api_2.3.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.activemq.artemis_2.7.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.app_1.5.0.v20200717-0620.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.linux.systemd.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.linux.clock_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.camel.sun.misc_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.logging.log4j.core_2.17.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.console_1.4.200.v20200828-1034.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.soda.dk.comm.aarch64_1.3.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.camel.cloud.factory_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.localization_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.linux.position_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/jakarta.xml.bind-api_2.3.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.camel.camel-core_2.25.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.util_1.1.300.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.http.registry_1.2.0.v20200614-1851.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.broker.artemis.core_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.soda.dk.comm.x86_64_1.3.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.felix.gogo.command_1.0.2.v20170914-1324.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.jetty.util_9.4.44.v20210927.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.gwt.user_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.geronimo.specs.geronimo-jta_1.1_spec_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.bidi_1.3.0.v20200612-1624.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.crypto_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/io.netty.transport_4.1.68.Final.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.felix.gogo.shell_1.1.0.v20180713-1646.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.useradmin.store_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.felix.useradmin_1.0.4.k1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.eclipsesource.jaxrs.publisher_5.3.1.201602281253.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.linux.watchdog_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.servicemix.bundles.spring-jms_4.3.20.RELEASE_1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.camel.camel-jms_2.25.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.linux.debian.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.api_2.3.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.supplement_1.10.0.v20200612-0806.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.wire.component.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.tamper.detection_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.metatype_1.5.300.v20200422-1833.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.driver.helper.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/io.netty.handler_4.1.68.Final.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.osgi.services_3.9.0.v20200511-1725.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.cloud.factory_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.h2database_2.1.214.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.commons.beanutils_1.9.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.knowhowlab.osgi.monitoradmin_1.0.2.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.codeminders.hidapi.armv6hf_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.common_3.13.0.v20200828-1034.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.core.contenttype_3.7.800.v20200724-0804.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/bcpkix_1.65.0.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.felix.dependencymanager_3.0.0.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/io.netty.transport-native-unix-common_4.1.68.Final.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/io.netty.codec-http_4.1.68.Final.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.misc.cloudcat_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.http.servlet_1.6.600.v20200707-1543.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.activemq.artemis-mqtt-protocol_2.6.4.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/javax.servlet_3.1.0.v201410161800.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.usb4java_1.3.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.util_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/javax.servlet.jsp_2.2.0.v201112011158.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.coordinator_1.3.800.v20200422-1833.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.eurotech.gpsd4java_1.0.0.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/slf4j.api_1.7.32.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.keystore_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.wire.camel_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.weaving.caching_1.1.400.v20200422-1833.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.jetty.continuation_9.4.44.v20210927.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.wire.component.conditional.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.logging.log4j.api_2.17.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.osgi_3.16.0.v20200828-0759.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.jetty.util.ajax_9.4.44.v20210927.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.activemq.artemis-native_2.6.4.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.logging.log4j.slf4j-impl_2.17.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/jdk.dio_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.geronimo.specs.geronimo-json_1.0_spec_1.0.0.alpha-1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.launcher_1.5.800.v20200727-1323.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.jetty.http_9.4.44.v20210927.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.deployment_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/osgi.annotation_6.0.1.201503162037.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.deployment.agent_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.request.handler.jaxrs_1.0.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.eclipsesource.jaxrs.jersey-min_2.22.2.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.ble.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.jetty.server_9.4.44.v20210927.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.container.orchestration.provider_1.0.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.linux.usb.aarch64_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.core.expressions_3.7.0.v20200720-1126.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.region_1.5.0.v20200807-1629.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.eclipsesource.jaxrs.provider.security_2.2.0.201602281253.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.certificates_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.camel.camel-script_2.25.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.commons.commons-net_3.8.0.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.codeminders.hidapi_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.jsp.jasper.registry_1.1.400.v20200422-1833.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.network.threat.manager_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.broker.artemis.xml_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.linux.command_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.transforms.hook_1.2.500.v20190714-1852.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.system_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.linux.gpio_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.servicemix.bundles.spring-beans_4.3.20.RELEASE_1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.eclipsesource.jaxrs.provider.multipart_2.2.0.201602281253.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.servicemix.bundles.spring-core_4.3.20.RELEASE_1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/io.netty.transport-native-kqueue_4.1.68.Final.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/jakarta.xml.soap-api_1.4.2.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/bluez-dbus-osgi_0.1.4.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/io.netty.resolver_4.1.68.Final.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/io.netty.common_4.1.68.Final.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.json.marshaller.unmarshaller.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.tigris.mtoolkit.iagent.rpc_3.0.0.20110411-0918.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.commons.exec_1.3.0.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.felix.deploymentadmin_0.9.5.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.asset.helper.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.google.guava_25.0.0.jre.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.google.gson_2.7.0.v20170129-0911.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/jcl.over.slf4j_1.7.32.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.soda.dk.comm.armv6hf_1.3.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.geronimo.specs.geronimo-jms_2.0_spec_1.0.0.alpha-2.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.rest.configuration.provider_1.0.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.codeminders.hidapi.aarch64_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.asset.cloudlet.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.jetty.customizer_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.camel.camel-stream_2.25.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.wire.h2db.component.provider_2.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.hook.file.move.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.felix.gogo.runtime_1.1.0.v20180713-1646.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.rest.provider_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.registry_3.9.0.v20200625-1425.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.commons.fileupload_1.3.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.wire.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.glassfish.hk2.osgi-resource-locator_1.0.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.xml.marshaller.unmarshaller.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.weaving.caching.j9_1.1.400.v20200422-1833.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.weaving.hook_1.2.700.v20200422-1833.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.linux.usb_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.google.protobuf_3.19.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.http.jetty_3.7.400.v20200123-1333.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.concurrent_1.1.500.v20200106-1437.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.frameworkadmin_2.1.400.v20191002-0702.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.commons.collections_3.2.2.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.ble.eddystone.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.servicemix.bundles.spring-context_4.3.20.RELEASE_1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.servicemix.bundles.spring-tx_4.3.20.RELEASE_1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/io.netty.codec-mqtt_4.1.68.Final.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core_1.1.2.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.core.jobs_3.10.800.v20200421-0950.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.core.runtime_3.19.0.v20200724-1004.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/jakarta.activation-api_1.2.2.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.jetty.servlet_9.4.44.v20210927.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.sun.misc_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.inventory_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.felix.scr_2.1.16.v20200110-1820.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.qpid.jms.client_0.45.0.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.wire.component.join.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.camel_1.4.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.broker.artemis.simple.mqtt_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.asset.provider_2.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.qpid.proton-j_0.33.2.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.status_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.soda.dk.comm_1.3.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/jdk.dio.x86_64_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.cloudconnection.raw.mqtt.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/io.netty.codec_4.1.68.Final.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.ble.ibeacon.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.linux.bluetooth_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.localization.resources_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.io_1.1.100.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.cloud_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.log.filesystem.provider_1.0.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.servicemix.bundles.spring-expression_4.3.20.RELEASE_1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.commons.csv_1.4.0.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.ds_1.6.200.v20200422-1833.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.console.jaas.fragment_1.0.300.v20200111-0718.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/javax.el_2.2.0.v201303151357.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.jboss.logging.jboss-logging_3.3.2.Final.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.linux.net_2.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/jdk.dio.armv6hf_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.hamcrest.core_1.3.0.v201303031735.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.camel.camel-core-osgi_2.25.3.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.event_1.5.500.v20200616-0800.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.jetty.security_9.4.44.v20210927.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.net_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.transforms.xslt_1.1.100.v20200422-1833.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.core.comm_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/bcprov_1.65.0.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.wire.helper.provider_1.1.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.preferences_3.8.0.v20200422-1833.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.equinox.wireadmin_1.0.800.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/com.codeminders.hidapi.x86_64_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.osgi.util_3.5.300.v20190708-1141.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.camel.xml_1.2.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.rest.wire.provider_1.0.1.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.apache.commons.commons-io_2.11.0.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.jetty.io_9.4.44.v20210927.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/plugins/org.eclipse.kura.web2_2.1.2.jar \
    /opt/eclipse/kura_5.1.2_solidsense-igw/log4j/log4j.xml \
    /opt/eclipse/kura_5.1.2_solidsense-igw/user/kura_custom.properties \
    /opt/eclipse/kura_5.1.2_solidsense-igw/user/security \
    /opt/eclipse/kura_5.1.2_solidsense-igw/user/snapshots \
    /opt/eclipse/kura_5.1.2_solidsense-igw/user/security/cacerts.ks \
    /opt/eclipse/kura_5.1.2_solidsense-igw/user/snapshots/snapshot_0.xml \
    /opt/eclipse/kura_5.1.2_solidsense-igw/console/skin \
    /opt/eclipse/kura_5.1.2_solidsense-igw/console/skin/favicon.ico \
    /opt/eclipse/kura_5.1.2_solidsense-igw/console/skin/favicon-16x16.png \
    /opt/eclipse/kura_5.1.2_solidsense-igw/console/skin/favicon-96x96.png \
    /opt/eclipse/kura_5.1.2_solidsense-igw/console/skin/favicon-32x32.png \
    /usr/share \
    /usr/share/polkit-1 \
    /usr/share/polkit-1/rules.d \
    /usr/share/polkit-1/rules.d/kura.rules \
    /var \
    /etc \
    /var/named \
    /var/named/named.ca \
    /etc/named.rfc1912.zones \
    /etc/logrotate-kura.conf \
    /etc/dhcpd-eth0.conf \
    /etc/dhcpd-wlan0.conf \
    /etc/tmpfiles.d \
    /etc/sysconfig \
    /etc/cron.d \
    /etc/bind \
    /etc/network \
    /etc/dbus-1 \
    /etc/apparmor.d \
    /etc/tmpfiles.d/kura.conf \
    /etc/sysconfig/iptables \
    /etc/cron.d/logrotate-kura \
    /etc/bind/named.conf \
    /etc/network/interfaces \
    /etc/network/if-down.d \
    /etc/network/if-up.d \
    /etc/network/if-down.d/idown-local \
    /etc/network/if-up.d/ifup-local \
    /etc/dbus-1/system.d \
    /etc/dbus-1/system.d/bluetooth.conf \
    /etc/apparmor.d/usr.sbin.named \
"
