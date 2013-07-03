package net.ripe.db.whois.api.whois.rdap;

import com.google.common.collect.Maps;
import com.google.common.collect.Lists;
import net.ripe.db.whois.api.whois.TaggedRpslObject;
import net.ripe.db.whois.api.whois.rdap.domain.Autnum;
import net.ripe.db.whois.api.whois.rdap.domain.Domain;
import net.ripe.db.whois.api.whois.rdap.domain.Entity;
import net.ripe.db.whois.api.whois.rdap.domain.Events;
import net.ripe.db.whois.api.whois.rdap.domain.Ip;
import net.ripe.db.whois.api.whois.rdap.domain.Nameservers;
import net.ripe.db.whois.api.whois.rdap.domain.ObjectFactory;
import net.ripe.db.whois.api.whois.rdap.domain.RdapObject;
import net.ripe.db.whois.api.whois.rdap.domain.Remarks;
import net.ripe.db.whois.common.domain.attrs.AsBlockRange;
import net.ripe.db.whois.common.rpsl.AttributeType;
import net.ripe.db.whois.common.rpsl.ObjectType;
import net.ripe.db.whois.common.rpsl.RpslAttribute;
import net.ripe.db.whois.common.rpsl.RpslObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class RdapObjectMapper {
    private static final Logger LOGGER = 
        LoggerFactory.getLogger(RdapObjectMapper.class);

    ObjectFactory rdapObjectFactory = new ObjectFactory();

    private TaggedRpslObject primaryTaggedRpslObject;
    private Queue<TaggedRpslObject> taggedRpslObjectQueue;
    private RdapObject rdapResponse;
    private DatatypeFactory dtf = DatatypeFactory.newInstance();
    private static List<String> RDAPCONFORMANCE = 
        Lists.newArrayList("rdap_level_0");

    public RdapObjectMapper(Queue<TaggedRpslObject> taggedRpslObjectQueue)
            throws DatatypeConfigurationException {
        this.taggedRpslObjectQueue = taggedRpslObjectQueue;
    }

    public Object build() throws Exception {
        if (taggedRpslObjectQueue == null) {
            return rdapResponse;
        }

        if (taggedRpslObjectQueue.isEmpty()) {
            throw new Exception("The RPSL queue is empty.");
        }

        TaggedRpslObject first = taggedRpslObjectQueue.poll();
        add(first, taggedRpslObjectQueue);

        return rdapResponse;
    }

    private void add(TaggedRpslObject taggedRpslObject,
                     Queue<TaggedRpslObject> taggedRpslObjectQueue)
            throws ParseException {
        RpslObject rpslObject = taggedRpslObject.rpslObject;
        ObjectType rpslObjectType = rpslObject.getType();

        debug(rpslObject);

        switch (rpslObjectType) {
            case DOMAIN:
                rdapResponse = createDomainResponse(rpslObject);
                break;
            case AUT_NUM:
            case AS_BLOCK:
                rdapResponse = createAutnumResponse(rpslObject,
                                                    taggedRpslObjectQueue);
                break;
            case INETNUM:
            case INET6NUM:
                Ip ip = new ObjectFactory().createIp();
                ip.setHandle(rpslObject.getKey().toString());
                rdapResponse = ip;
                break;
            case PERSON:
            case ORGANISATION:
                rdapResponse = createEntity(rpslObject);
                break;
            case ROLE:
            case IRT:
                break;
        };

        if (rdapResponse != null) {
            rdapResponse.getRdapConformance().addAll(RDAPCONFORMANCE);
        }
    }

    private void setRemarks (RdapObject rdapObject, RpslObject rpslObject) {
        List<RpslAttribute> remarks =
                rpslObject.findAttributes(AttributeType.REMARKS);
        List<RpslAttribute> descrs =
                rpslObject.findAttributes(AttributeType.DESCR);
        List<RpslAttribute> allRemarks =  new ArrayList<RpslAttribute>();
        allRemarks.addAll(remarks);
        allRemarks.addAll(descrs);

        List<String> remarkList = new ArrayList<>();
        Remarks remark = rdapObjectFactory.createRemarks();

        for (RpslAttribute rpslAttribute : allRemarks) {
            String descr = rpslAttribute.getCleanValue().toString();

            remarkList.add(descr);
        }

        remark.getDescription().addAll(remarkList);
        rdapObject.getRemarks().add(remark);
    }

    private void setEvents (RdapObject rdapObject, RpslObject rpslObject) 
            throws ParseException {
        List<RpslAttribute> changedAttributes = 
            rpslObject.findAttributes(AttributeType.CHANGED);
        int listSize = changedAttributes.size();

        RpslAttribute lastChanged = changedAttributes.get(listSize - 1);

        Events event = rdapObjectFactory.createEvents();
        String eventString = lastChanged.getValue();

        // Split the string and make the event entry.
        eventString = eventString.trim();
        String[] eventStringElements = eventString.split(" ");
        event.setEventAction("last changed");
        event.setEventActor(eventStringElements[0]);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        Date date = formatter.parse(eventStringElements[1]);
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTime(date);
        XMLGregorianCalendar eventDate = dtf.newXMLGregorianCalendar(gc);

        event.setEventDate(eventDate);

        rdapObject.getEvents().add(event);
    }

    private Entity createEntity(RpslObject rpslObject) {
        Entity entity = rdapObjectFactory.createEntity();
        entity.setHandle(rpslObject.getKey().toString());
        entity.setVcardArray(generateVcards(rpslObject));
        
        setRemarks(entity,rpslObject);

        try {
            setEvents(entity,rpslObject);
        } catch (ParseException p) {
            // DIE!
        }

        return entity;
    }

    private void setEntities(RdapObject rdapObject,
                             RpslObject rpslObject,
                             Queue<TaggedRpslObject> qtro,
                             Set<AttributeType> eats) {
        HashMap<String, RpslObject> mtro = new HashMap<String, RpslObject>();
        for (TaggedRpslObject tro : qtro) {
            mtro.put(tro.rpslObject.getKey().toString(),
                     tro.rpslObject);
        }
        List<RpslAttribute> eas = new ArrayList<RpslAttribute>();
        for (AttributeType eat : eats) {
            eas.addAll(rpslObject.findAttributes(eat));
        }
        Set<String> seen = new HashSet<String>();
        for (RpslAttribute ra : eas) {
            String key = ra.getCleanValue().toString();
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            RpslObject ro = mtro.get(key);
            if (ro != null) {
                Entity e = createEntity(ro);
                rdapObject.getEntities().add(e);
            }
        }
    }

    private Autnum createAutnumResponse(RpslObject rpslObject,
                                        Queue<TaggedRpslObject> qtro)
            throws ParseException {

        Autnum an = rdapObjectFactory.createAutnum();
        an.setHandle(rpslObject.getKey().toString());

        boolean is_autnum =
            rpslObject.getType().getName().equals(
                ObjectType.AUT_NUM.getName()
            );

        BigInteger start;
        BigInteger end;
        if (is_autnum) {
            RpslAttribute asn =
                rpslObject.findAttribute(AttributeType.AUT_NUM);
            String asn_str = asn.getValue().replace("AS", "").replace(" ", "");
            start = end = new BigInteger(asn_str);
        } else {
            RpslAttribute asn_range =
                rpslObject.findAttribute(AttributeType.AS_BLOCK);
            AsBlockRange abr = 
                AsBlockRange.parse(asn_range.getValue().replace(" ", ""));
            start = BigInteger.valueOf(abr.getBegin());
            end   = BigInteger.valueOf(abr.getEnd());
        }

        an.setStartAutnum(start);
        an.setEndAutnum(end);

        an.setCountry(rpslObject.findAttribute(AttributeType.COUNTRY)
                                .getValue().replace(" ", ""));
        
        /* For as-blocks, use the range as the name, since they do not
         * have an obvious 'name' attribute. */
        AttributeType name =
            (is_autnum)
                ? AttributeType.AS_NAME
                : AttributeType.AS_BLOCK; 
        an.setName(rpslObject.findAttribute(name)
                             .getValue().replace(" ", ""));
        /* aut-num records don't have a 'type' or 'status' field, and
         * each is allocated directly by the relevant RIR. 'DIRECT
         * ALLOCATION' is the default value used in the response
         * draft, and it makes sense to use it here too, at least for
         * now. */
        an.setType("DIRECT ALLOCATION");
        /* None of the statuses from [9.1] in json-response is
         * applicable here, so 'status' will be left empty for now. */

        setRemarks(an, rpslObject);
        setEvents(an, rpslObject);

        Set<AttributeType> eats = new HashSet<AttributeType>();
        eats.add(AttributeType.ADMIN_C);
        eats.add(AttributeType.TECH_C);
        setEntities(an, rpslObject, qtro, eats);

        return an;
    }

    private Domain createDomainResponse(RpslObject rpslObject) {
        Domain domain = rdapObjectFactory.createDomain();

        domain.setHandle(rpslObject.getKey().toString());
        domain.setLdhName(rpslObject.getKey().toString());

        // Nameservers
        for  (RpslAttribute rpslAttribute : rpslObject.findAttributes(AttributeType.NSERVER)) {
            Nameservers ns = rdapObjectFactory.createNameservers();
            ns.setLdhName(rpslAttribute.getCleanValue().toString());
            domain.getNameservers().add(ns);
        }

        setRemarks(domain, rpslObject);

//        // Entities
//        Entity entity = rdapObjectFactory.createEntity();
//        entity.getEntities().add()


//        "entities" :
//
//        //domain.setPort43();
//        domain.getPublicIds();
//        domain.getEntities();
//        domain.getEvents()
//        domain.getLinks()
//        domain.getNameserver()
//        domain.getRemarks()

//        {
//            "handle" : "XXX",
//                "ldhName" : "blah.example.com",
//            ...
//            "nameServers" :
//            [
//            ...
//            ],
//            ...
//            "entities" :
//            [
//            ...
//            ]
//        }
        return domain;

    }

    private void debug(RpslObject rpslObject) {
        List<RpslAttribute> rpslAttributes = rpslObject.getAttributes();

        Iterator<RpslAttribute> iter = rpslAttributes.iterator();
        RpslAttribute ra;
        while (iter.hasNext()) {
            ra = iter.next();
            LOGGER.info(ra.getKey() + ":" + ra.getValue());
        }
    }

    private List<Object> generateVcards(RpslObject rpslObject) {
        VcardObjectHelper.VcardBuilder builder = new VcardObjectHelper.VcardBuilder();
        builder.setVersion();

       for (RpslAttribute attribute : rpslObject.findAttributes(AttributeType.PERSON)) {
            builder.setFn(attribute.getCleanValue().toString());
        }

        for (RpslAttribute attribute : rpslObject.findAttributes(AttributeType.ADDRESS)) {
            builder.addAdr(VcardObjectHelper.createHashMap(Maps.immutableEntry("label", attribute.getCleanValue())), null);
        }

        for (RpslAttribute attribute : rpslObject.findAttributes(AttributeType.PHONE)) {
            builder.addTel(attribute.getCleanValue().toString());
        }

        for (RpslAttribute attribute : rpslObject.findAttributes(AttributeType.E_MAIL)) {
            // ?? Is it valid to have more than 1 email
            builder.setEmail(attribute.getCleanValue().toString());
        }

        if (builder.isEmpty()) {
            return null;
        }

        return builder.build();
    }

//    private String attributeListToString(List<RpslAttribute> rpslAttributes) {
//        StringBuilder ret = new StringBuilder();
//        for (RpslAttribute rpslAttribute : rpslAttributes) {
//            ret.append(rpslAttribute.getCleanValue()).append(" ");
//        }
//        return ret.toString().trim();
//    }


}
